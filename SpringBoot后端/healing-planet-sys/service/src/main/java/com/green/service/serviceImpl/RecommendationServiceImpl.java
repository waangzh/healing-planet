package com.green.service.serviceImpl;

import com.green.common.exception.DeviceKeyError;
import com.green.common.exception.DevicePurchasedError;
import com.green.mapper.PostLikesMapper;
import com.green.mapper.RecommendationMapper;
import com.green.mapper.UserPostViewMapper;
import com.green.dto.PostSummaryDTO;
import com.green.dto.UserPurchaseInfoDTO;
import com.green.dto.UserYouMayKnowDTO;
import com.green.entity.User;
import com.green.entity.UserPurchaseTags;
import com.green.vo.RecommendPostVO;
import com.green.vo.UserBindDeviceVO;
import com.green.service.RecommendationService;
import com.green.utils.BaiDuUtil;
import com.green.utils.HttpClientUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.commons.util.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.green.controller.RecommendationController.*;

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


    // 单例 ObjectMapper，线程安全
    private static final ObjectMapper mapper = new ObjectMapper();


    /**
     * 从 LLM 返回的字符串中提取纯 ID 列表
     *
     * @param baiDu LLM 原始返回文本，可能是完整 JSON 对象或包含 Markdown 代码块
     * @return 提取并去重后的文章 ID 列表
     * @throws IOException 提取或解析失败时抛出
     */
    public static List<String> parseRecommendedIds(String baiDu) throws IOException {
        // 1. 尝试解析为 JSON，若包含 "result" 字段则取其文本值
        String content = baiDu;
        try {
            JsonNode root = mapper.readTree(baiDu);
            JsonNode resultNode = root.get("result");
            if (resultNode != null && resultNode.isTextual()) {
                content = resultNode.asText();
            }
        } catch (IOException ignored) {
            // 解析失败则视为纯文本，使用 baiDu 原文
        }

        // 2. 匹配并提取代码块内的 JSON 数组
        Pattern fenced = Pattern.compile("(?s)```json\\s*(\\[.*?\\])\\s*```", Pattern.DOTALL);
        Matcher m = fenced.matcher(content);
        String jsonArray;
        if (m.find()) {
            jsonArray = m.group(1);
        } else {
            // 如果没有 Markdown 包裹，宽松匹配首个 JSON 数组
            Pattern arrOnly = Pattern.compile("(?s)(\\[.*?\\])", Pattern.DOTALL);
            m = arrOnly.matcher(content);
            if (m.find()) {
                jsonArray = m.group(1);
            } else {
                throw new IllegalArgumentException("未找到 JSON 数组部分: " + baiDu);
            }
        }

        // 3. 反序列化为 List<String>
        CollectionType listType = mapper.getTypeFactory()
                .constructCollectionType(List.class, String.class);
        // 移除单行注释 (//) 和多行注释 (/* */)
        String cleanJson = jsonArray.replaceAll("//.*|/\\*(?s:.*?)\\*/", "");
        List<String> ids = mapper.readValue(jsonArray, listType);

        // 4. 去重并返回
        return ids.stream()
                .distinct()
                .collect(Collectors.toList());
    }


    private List<String> getPlantsName(User communityUser) throws IOException {
        String url = baseUrl + "/data/plants";
        Map<String, String> params = new HashMap<>();
        params.put("userId", communityUser.getId());
        String doPost = HttpClientUtil.doPost(url, params);


        ObjectMapper mapper = new ObjectMapper();
        // 1. 解析成 JsonNode
        JsonNode root = mapper.readTree(doPost);
        // 2. 拿到 data 节点
        JsonNode dataNode = root.path("data");


        // 3. 转成 List<String>
        List<String> plantList = mapper.convertValue(
                dataNode,
                new TypeReference<List<String>>() {
                }
        );

        return plantList;
    }

    /**
     * 将用户列表转换为UserYouMayKonwDTO列表
     */

    @SneakyThrows
    private List<UserYouMayKnowDTO> convertUsersToDtos(List<User> users) {
        return users.stream()
                .map(this::convertUserToDto)
                .collect(Collectors.toList());
    }

    /**
     * 将单个用户转换为UserYouMayKonwDTO
     */
    @SneakyThrows
    private UserYouMayKnowDTO convertUserToDto(User user) {
        UserYouMayKnowDTO dto = new UserYouMayKnowDTO();
        BeanUtils.copyProperties(user, dto);

        // 检查是否购买设备
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
        //向后台发送请求，获得账号和密码
        HashMap<String, String> params = new HashMap<>();
        params.put("communityUserId", communityUser.getId());
        params.put("deviceKey", deviceKey);
        String url = baseUrl + "deviceBind/getKey";
        log.info("url:{}", url);
        String response = HttpClientUtil.doPost(url, params);

        log.info("获得的信息是:{}", response);

        // 解析JSON并转换
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);
        int success = rootNode.path("code").asInt();

        if (success != 1)
            throw new DeviceKeyError(rootNode.path("msg").asText());

        JsonNode data = rootNode.path("data");
        String account = data.path("account").asText();
        String password = data.path("password").asText();
        Integer deviceId = Integer.valueOf(data.path("deviceId").asText());
        Integer backEndUserId = Integer.valueOf(data.path("backEndUserId").asText());

        //更新用户_购买_推荐表的数据
        userPurchaseTags = userPurchaseTags.toBuilder()
                .communityUserId(communityUser.getId())
                .isPurchased(true)
                .recommendTags("已经购买设备，并开始种植绿植了，是绿植养殖者")
                .deviceKey(deviceKey)
                .backEndUserId(backEndUserId)
                .deviceId(deviceId)
                .account(account)
                .build();

        //一个设备只能被一个用户绑定吧，先这样设定。
        List<Integer> existsBackEndUser = recommendationMapper.existsBackEndUser(communityUser.getId());

        //如果这个deviceKey在这个表里面已经有了，说明已经绑定过了，因为后台是创建一个设备就直接在他的表里面插入的，逻辑上说的通
        Boolean exitsKey = recommendationMapper.existsKey(deviceKey);

        if (!existsBackEndUser.isEmpty() && !exitsKey) {
            //如果后台关联用户大于1的话
            recommendationMapper.addCommunityUser(userPurchaseTags);
        }

        if (existsBackEndUser.isEmpty()) {
            //如果暂无后台关联用户的话
            recommendationMapper.updateUser(userPurchaseTags);
        }

        return UserBindDeviceVO.builder()
                .account(account)
                .password(password)
                .build();

    }

    /**
     * 文章推荐接口
     *
     * @param pageNo
     * @param pageSize
     * @param communityUser
     * @return
     */
    @Override
    @Transactional
    public List<RecommendPostVO> getPostsRecommendations(Integer pageNo, Integer pageSize, User communityUser)
            throws JsonProcessingException {

        // 1. 从Redis获取文章ID列表的JSON字符串
        String postsJson = (String) redisTemplate.opsForValue().get(POSTS_KEY + communityUser.getId());
        log.info("从Redis获取的文章ID列表: {}", postsJson);

        // 如果Redis中没有数据，返回空列表
        if (StringUtils.isBlank(postsJson)) {
            log.warn("未找到用户{}的文章推荐缓存", communityUser.getId());
            return Collections.emptyList();
        }

        // 2. 使用Jackson将JSON反序列化为List<String>
        ObjectMapper mapper = new ObjectMapper();
        List<String> allPostIds;
        try {
            allPostIds = mapper.readValue(postsJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("文章ID列表JSON解析失败", e);
            return Collections.emptyList();
        }
        log.info("解析后的文章ID列表: {}", allPostIds);

        // 如果列表为空，返回空列表
        if (CollectionUtils.isEmpty(allPostIds)) {
            return Collections.emptyList();
        }

        // 3. 随机打乱列表顺序（使用时间戳作为随机种子）
        Collections.shuffle(allPostIds, new Random(System.currentTimeMillis()));

        // 4. 处理分页逻辑
        Random random = new Random((long) pageNo *pageSize);
        int startIndex = random.nextInt(4);

        int endIndex = Math.min(startIndex + pageSize, allPostIds.size());
        List<String> selectedPostIds = allPostIds.subList(startIndex, endIndex);

        // 5. 通过MyBatis查询完整的文章信息
        return recommendationMapper.getPostsByList(selectedPostIds);
    }

    /**
     * 猜你喜欢页面，根据大模型推荐文章id
     *
     * @param communityUser
     */
    @Override
    public void LLMRecommendationPosts(User communityUser) throws Exception {
        //首先就要区分开，购买了设备的用户获取的信息和未购买设备的用户获取的信息是不一样的

        /*
         * 1.首先是未购买设备的用户
         * 需要查的表
         * 浏览历史表
         *   浏览历史相关的文章的id，标题，标签，评论数等内容
         * 推荐信息表
         *   当前用户的各种内容
         * */

        // 查询用户浏览历史
        List<PostSummaryDTO> userPostLogsList = userPostViewMapper.getViewsByUserId(communityUser.getId());

        // 查询文章列表，近15天的
        List<PostSummaryDTO> allPostSummary = userPostViewMapper.getPosts();

        //log.info("获取用户浏览记录:{}", userPostLogsList);
        //log.info("获取近15天文章信息:{}", allPostSummary);

        //获取该用户是否购买设备
        List<Integer> existsBackEndUser = recommendationMapper.existsBackEndUser(communityUser.getId());

        UserPurchaseInfoDTO userPurchaseInfoDTO = new UserPurchaseInfoDTO();
        List<String> result = null;

        if (!existsBackEndUser.isEmpty()) {

            List<String> plantList = getPlantsName(communityUser);

            userPurchaseInfoDTO = userPurchaseInfoDTO.toBuilder()
                    .isPurchasedDevice(true)
                    .plantNames(plantList)
                    .recommendTags("已经购买设备开始种植，绿植了")
                    .build();

            //询问大模型
            String baiDu = baiDuUtil.recommendPosts(userPostLogsList, allPostSummary, userPurchaseInfoDTO, true);
            result = parseRecommendedIds(baiDu);

            //log.info("大模型查询结果是：{}", result);

        } else {
            userPurchaseInfoDTO = userPurchaseInfoDTO.toBuilder()
                    .isPurchasedDevice(true)
                    .plantNames(null)
                    .recommendTags("暂未购买设备，还未开始种植绿植")
                    .build();
            //询问大模型
            String baiDu = baiDuUtil.recommendPosts(userPostLogsList, allPostSummary, userPurchaseInfoDTO, false);
            result = parseRecommendedIds(baiDu);
            //log.info("LLM查询结果是：{}", result);
        }

        //将结果存入缓存当中，方便查询
        redisTemplate.opsForValue().set(POSTS_KEY + communityUser.getId(), result.toString(), Duration.ofDays(1L));

    }

    /**
     * 大模型推荐你可能喜欢的用户
     *
     * @param communityUser
     */
    @Override
    public void LLMRecommendationUsers(User communityUser) throws Exception {
        // 先查询你自己的信息，在查询你关注了的人的信息
        // 关注的用户，你自己的标签，你是否购买了设备，你种植的是什么植物这种，还有你们收藏或者喜欢的文章的标题和标签是什么这种
        List<User> followedUsers = recommendationMapper.getUsersYouFollowed(communityUser.getId());
        followedUsers.add(communityUser); // 集合的最后一个是当前用户

        // 转换为DTO列表
        List<UserYouMayKnowDTO> followedUserDtos = convertUsersToDtos(followedUsers);


        // 获取所有用户并转换为DTO
        List<User> allUsers = recommendationMapper.getUsers();
        List<UserYouMayKnowDTO> allUserDtos = convertUsersToDtos(allUsers);
//        log.info("你关注的人：{}", followedUserDtos);
//        log.info("随机的50个用户：{}", allUserDtos);
        // 调用大模型
        String baiDu = baiDuUtil.recommendFollowees(followedUserDtos, allUserDtos);
        log.info("百度大模型返回的信息：{}", baiDu);
        List<String> result = parseRecommendedIds(baiDu);
        log.info("返回结果：{}", result);

        //将结果存入缓存当中，方便查询
        redisTemplate.opsForValue().set(USER_KEY + communityUser.getId(), result.toString(), Duration.ofDays(1L));

    }

    /**
     * 推荐用户接口
     *
     * @param pageNo
     * @param pageSize
     * @param communityUser
     * @return
     */
    @Override
    @Transactional
    public List<User> getUsersRecommendations(Integer pageNo, Integer pageSize, User communityUser) throws JsonProcessingException {


        // 1. 从Redis获取用户ID列表的JSON字符串
        String usersJson = (String) redisTemplate.opsForValue().get(USER_KEY + communityUser.getId());

        // 如果Redis中没有数据，返回空列表
        if (StringUtils.isBlank(usersJson)) {
            return Collections.emptyList();
        }

        // 2. 使用Jackson将JSON反序列化为List<String>
        ObjectMapper mapper = new ObjectMapper();
        List<String> allUserIds = mapper.readValue(usersJson, new TypeReference<List<String>>() {});

        // 如果列表为空，返回空列表
        if (CollectionUtils.isEmpty(allUserIds)) {
            return Collections.emptyList();
        }

        Collections.shuffle(allUserIds, new Random(System.currentTimeMillis()));
        // 4. 处理分页逻辑

        Random random = new Random((long) pageNo *pageSize);
        int startIndex = random.nextInt(4);

        int endIndex = Math.min(startIndex + pageSize, allUserIds.size());
        List<String> selectedIds = allUserIds.subList(startIndex, endIndex);

        // 5. 通过MyBatis查询完整的用户信息
        return recommendationMapper.getUsersByList(selectedIds);
    }

    /**
     * 实现大模型智能写日志的功能
     *
     * @param user
     * @param message
     * @return
     */
    @Override
    public String writePost(User user, String message) throws IOException {

        //先查询有没有购买设备，如果有的话在进行查询
        List<Integer> backEndUserId = recommendationMapper.existsBackEndUser(user.getId());
        if (backEndUserId.isEmpty())
            throw new DevicePurchasedError("您暂未购买设备，该功能暂不对外开放");

        Map<String, String> params = new HashMap<>();
        params.put("communityUserId", user.getId());
        params.put("msg", message);
        String url = baseUrl + "data/post";
        log.info("网址是：{}", url);
        String response = HttpClientUtil.doPost(url, params);
        log.info("后台返回的响应:{}", response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);

// 1. 先获取"data"字段（它是一个字符串形式的JSON）
        String dataStr = rootNode.path("data").asText();

// 2. 将"data"字符串再次解析为JsonNode
        JsonNode dataNode = objectMapper.readTree(dataStr);

// 3. 从dataNode中获取"result"字段
        String result = dataNode.path("result").asText();
        log.info("data中的内容是：{}", result);
        return result;
    }


}



