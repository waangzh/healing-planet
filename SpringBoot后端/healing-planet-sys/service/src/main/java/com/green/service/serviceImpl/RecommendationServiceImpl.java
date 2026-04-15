package com.green.service.serviceImpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.green.common.exception.DeviceKeyError;
import com.green.common.exception.DevicePurchasedError;
import com.green.dto.PostSummaryDTO;
import com.green.dto.UserPurchaseInfoDTO;
import com.green.dto.UserYouMayKnowDTO;
import com.green.entity.User;
import com.green.entity.UserPurchaseTags;
import com.green.mapper.PostLikesMapper;
import com.green.mapper.RecommendationMapper;
import com.green.mapper.UserPostViewMapper;
import com.green.service.RecommendationService;
import com.green.utils.BaiDuUtil;
import com.green.utils.HttpClientUtil;
import com.green.vo.RecommendPostVO;
import com.green.vo.UserBindDeviceVO;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.green.controller.RecommendationController.POSTS_KEY;
import static com.green.controller.RecommendationController.USER_KEY;

@Service
@Slf4j
public class RecommendationServiceImpl implements RecommendationService {

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Value("${shared.back-end.url}")
    private String baseUrl;

    @Autowired
    private UserPostViewMapper userPostViewMapper;

    @Autowired
    private BaiDuUtil baiDuUtil;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private PostLikesMapper postLikesMapper;

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从大模型返回内容中提取推荐 ID 列表。
     */
    public static List<String> parseRecommendedIds(String baiDu) throws IOException {
        String content = baiDu;
        try {
            JsonNode root = mapper.readTree(baiDu);
            JsonNode resultNode = root.get("result");
            if (resultNode != null && resultNode.isTextual()) {
                content = resultNode.asText();
            }
        } catch (IOException ignored) {
        }

        Pattern fenced = Pattern.compile("(?s)```json\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);
        Matcher m = fenced.matcher(content);
        String jsonArray;
        if (m.find()) {
            jsonArray = m.group(1);
        } else {
            Pattern arrOnly = Pattern.compile("(?s)(\\[.*?\\])", Pattern.DOTALL);
            m = arrOnly.matcher(content);
            if (m.find()) {
                jsonArray = m.group(1);
            } else {
                throw new IllegalArgumentException("未找到 JSON 数组: " + baiDu);
            }
        }

        CollectionType listType = mapper.getTypeFactory().constructCollectionType(List.class, String.class);
        String cleanJson = jsonArray.replaceAll("//.*|/\\*(?s:.*?)\\*/", "");
        List<String> ids = mapper.readValue(cleanJson, listType);
        return ids.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 解析缓存中的推荐 ID 列表，兼容 JSON 和旧格式字符串。
     */
    private List<String> parseIdListFromCache(String cacheValue) throws JsonProcessingException {
        if (!StringUtils.hasText(cacheValue)) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(cacheValue, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            String text = cacheValue.trim();
            if (text.startsWith("[") && text.endsWith("]")) {
                String body = text.substring(1, text.length() - 1).trim();
                if (body.isEmpty()) {
                    return Collections.emptyList();
                }
                return Arrays.stream(body.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            }
            throw ex;
        }
    }

    /**
     * 固定随机种子做分页切片，保证同一天同一用户分页稳定，并支持页码循环。
     */
    private List<String> selectPageStableRandom(List<String> ids, Integer pageNo, Integer pageSize, String seedKey) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }

        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 5 : pageSize;

        List<String> shuffled = new ArrayList<>(ids);
        long seed = Objects.hash(seedKey, LocalDate.now().toString());
        Collections.shuffle(shuffled, new Random(seed));

        int total = shuffled.size();
        int totalPages = (total + safePageSize - 1) / safePageSize;
        int normalizedPage = (safePageNo - 1) % totalPages;
        int start = normalizedPage * safePageSize;
        int end = Math.min(start + safePageSize, total);
        return new ArrayList<>(shuffled.subList(start, end));
    }

    private List<String> getPlantsName(User communityUser) throws IOException {
        String url = baseUrl + "/data/plants";
        Map<String, String> params = new HashMap<>();
        params.put("userId", communityUser.getId());
        String doPost = HttpClientUtil.doPost(url, params);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(doPost);
        JsonNode dataNode = root.path("data");
        return objectMapper.convertValue(dataNode, new TypeReference<List<String>>() {
        });
    }

    @SneakyThrows
    private List<UserYouMayKnowDTO> convertUsersToDtos(List<User> users) {
        return users.stream().map(this::convertUserToDto).collect(Collectors.toList());
    }

    @SneakyThrows
    private UserYouMayKnowDTO convertUserToDto(User user) {
        UserYouMayKnowDTO dto = new UserYouMayKnowDTO();
        BeanUtils.copyProperties(user, dto);
        List<Integer> purchaseList = recommendationMapper.existsBackEndUser(user.getId());
        if (!purchaseList.isEmpty()) {
            dto.setIsPurchased(true);
            List<String> plantList = getPlantsName(user);
            dto.setPlantsName(plantList.toString());
        }
        return dto;
    }

    @Override
    @Transactional
    public UserBindDeviceVO userBindDevice(String deviceKey, User communityUser) throws IOException {
        UserPurchaseTags userPurchaseTags = new UserPurchaseTags();
        HashMap<String, String> params = new HashMap<>();
        params.put("communityUserId", communityUser.getId());
        params.put("deviceKey", deviceKey);
        String url = baseUrl + "deviceBind/getKey";
        log.info("绑定设备请求地址: {}", url);
        String response = HttpClientUtil.doPost(url, params);
        log.info("绑定设备返回: {}", response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);
        int success = rootNode.path("code").asInt();
        if (success != 1) {
            throw new DeviceKeyError(rootNode.path("msg").asText());
        }

        JsonNode data = rootNode.path("data");
        String account = data.path("account").asText();
        String password = data.path("password").asText();
        Integer deviceId = Integer.valueOf(data.path("deviceId").asText());
        Integer backEndUserId = Integer.valueOf(data.path("backEndUserId").asText());

        userPurchaseTags = userPurchaseTags.toBuilder()
                .communityUserId(communityUser.getId())
                .isPurchased(true)
                .recommendTags("已购买设备并开始种植绿植")
                .deviceKey(deviceKey)
                .backEndUserId(backEndUserId)
                .deviceId(deviceId)
                .account(account)
                .build();

        List<Integer> existsBackEndUser = recommendationMapper.existsBackEndUser(communityUser.getId());
        Boolean exitsKey = recommendationMapper.existsKey(deviceKey);
        if (!existsBackEndUser.isEmpty() && !exitsKey) {
            recommendationMapper.addCommunityUser(userPurchaseTags);
        }
        if (existsBackEndUser.isEmpty()) {
            recommendationMapper.updateUser(userPurchaseTags);
        }

        return UserBindDeviceVO.builder().account(account).password(password).build();
    }

    @Override
    @Transactional
    public List<RecommendPostVO> getPostsRecommendations(Integer pageNo, Integer pageSize, User communityUser)
            throws JsonProcessingException {
        String postsJson = (String) redisTemplate.opsForValue().get(POSTS_KEY + communityUser.getId());
        log.info("文章推荐缓存内容: {}", postsJson);
        if (!StringUtils.hasText(postsJson)) {
            log.warn("用户 {} 的文章推荐缓存为空", communityUser.getId());
            return Collections.emptyList();
        }

        List<String> allPostIds;
        try {
            allPostIds = parseIdListFromCache(postsJson);
        } catch (JsonProcessingException e) {
            log.error("文章推荐缓存解析失败", e);
            return Collections.emptyList();
        }
        if (CollectionUtils.isEmpty(allPostIds)) {
            return Collections.emptyList();
        }

        List<String> selectedPostIds = selectPageStableRandom(
                allPostIds, pageNo, pageSize, "post:" + communityUser.getId());
        if (CollectionUtils.isEmpty(selectedPostIds)) {
            return Collections.emptyList();
        }
        log.info("本次返回文章ID: {}", selectedPostIds);
        return recommendationMapper.getPostsByList(selectedPostIds);
    }

    @Override
    public void LLMRecommendationPosts(User communityUser) throws Exception {
        List<PostSummaryDTO> userPostLogsList = userPostViewMapper.getViewsByUserId(communityUser.getId());
        List<PostSummaryDTO> allPostSummary = userPostViewMapper.getPosts();
        log.info("近15天可推荐文章数量: {}", allPostSummary.size());

        List<Integer> existsBackEndUser = recommendationMapper.existsBackEndUser(communityUser.getId());
        UserPurchaseInfoDTO userPurchaseInfoDTO = new UserPurchaseInfoDTO();
        List<String> result;

        if (!existsBackEndUser.isEmpty()) {
            List<String> plantList = getPlantsName(communityUser);
            userPurchaseInfoDTO = userPurchaseInfoDTO.toBuilder()
                    .isPurchasedDevice(true)
                    .plantNames(plantList)
                    .recommendTags("已购买设备并开始种植")
                    .build();

            String baiDu = baiDuUtil.recommendPosts(userPostLogsList, allPostSummary, userPurchaseInfoDTO, true);
            result = parseRecommendedIds(baiDu);
        } else {
            userPurchaseInfoDTO = userPurchaseInfoDTO.toBuilder()
                    .isPurchasedDevice(false)
                    .plantNames(null)
                    .recommendTags("暂未购买设备")
                    .build();
            String baiDu = baiDuUtil.recommendPosts(userPostLogsList, allPostSummary, userPurchaseInfoDTO, false);
            result = parseRecommendedIds(baiDu);
        }

        redisTemplate.opsForValue().set(
                POSTS_KEY + communityUser.getId(), mapper.writeValueAsString(result), Duration.ofDays(1L));
    }

    @Override
    public void LLMRecommendationUsers(User communityUser) throws Exception {
        List<User> followedUsers = recommendationMapper.getUsersYouFollowed(communityUser.getId());
        followedUsers.add(communityUser);

        List<UserYouMayKnowDTO> followedUserDtos = convertUsersToDtos(followedUsers);
        List<User> allUsers = recommendationMapper.getUsers();
        List<UserYouMayKnowDTO> allUserDtos = convertUsersToDtos(allUsers);

        String baiDu = baiDuUtil.recommendFollowees(followedUserDtos, allUserDtos);
        log.info("用户推荐模型返回: {}", baiDu);
        List<String> result = parseRecommendedIds(baiDu);
        log.info("用户推荐ID结果: {}", result);

        redisTemplate.opsForValue().set(
                USER_KEY + communityUser.getId(), mapper.writeValueAsString(result), Duration.ofDays(1L));
    }

    @Override
    @Transactional
    public List<User> getUsersRecommendations(Integer pageNo, Integer pageSize, User communityUser)
            throws JsonProcessingException {
        String usersJson = (String) redisTemplate.opsForValue().get(USER_KEY + communityUser.getId());
        if (!StringUtils.hasText(usersJson)) {
            return Collections.emptyList();
        }

        List<String> allUserIds = parseIdListFromCache(usersJson);
        if (CollectionUtils.isEmpty(allUserIds)) {
            return Collections.emptyList();
        }

        List<String> selectedIds = selectPageStableRandom(
                allUserIds, pageNo, pageSize, "user:" + communityUser.getId());
        if (CollectionUtils.isEmpty(selectedIds)) {
            return Collections.emptyList();
        }
        return recommendationMapper.getUsersByList(selectedIds);
    }

    @Override
    public String writePost(User user, String message) throws IOException {
        List<Integer> backEndUserId = recommendationMapper.existsBackEndUser(user.getId());
        if (backEndUserId.isEmpty()) {
            throw new DevicePurchasedError("您暂未购买设备，该功能暂不可用");
        }

        Map<String, String> params = new HashMap<>();
        params.put("communityUserId", user.getId());
        params.put("msg", message);
        String url = baseUrl + "data/post";
        log.info("写日志请求地址: {}", url);
        String response = HttpClientUtil.doPost(url, params);
        log.info("写日志返回: {}", response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);
        String dataStr = rootNode.path("data").asText();
        JsonNode dataNode = objectMapper.readTree(dataStr);
        String result = dataNode.path("result").asText();
        log.info("写日志结果: {}", result);
        return result;
    }
}
