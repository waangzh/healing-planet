package com.green.utils;

import com.green.dto.PostSummaryDTO;
import com.green.dto.UserPurchaseInfoDTO;
import com.green.dto.UserYouMayKnowDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Data
@AllArgsConstructor
@Slf4j
public class BaiDuUtil {
    private String API_KEY;
    private String SECRET_KEY;
    private String Multimodal_Api_Key;

    private static final String PROMPT_TEMPLATE = "您是一位专业的农业作物养护专家，请根据以下农业作物的环境数据，提供科学、详细且专业的养护方案：\n\n" +
            "1. **作物名称**: %s\n" +
            "2. **当前环境数据**:\n" +
            "   - 温度: %.2f°C\n" +
            "   - 土壤湿度: %.2f%%\n" +
            "   - 环境湿度: %.2f%%\n" +
            "   - 光照强度: %.2f Lux\n" +
            "   - 二氧化碳浓度: %.2f ppm\n" +
            "3. **作物种植区域**: %s\n" +
            "根据上述数据，请详细回答以下问题：\n" +
            "1. 基于当前环境和作物的需求，分析作物是否适宜在该环境中生长，若不适宜，请明确指出哪些环境因素需要调整（如温度、湿度、光照等）。\n" +
            "2. 根据当前环境数据，提供具体的养护建议，包含但不限于：\n" +
            "   - **温度管理**：该作物的理想温度范围是多少，是否需要调整温度或使用保护措施（如温室、遮阳等）？\n" +
            "   - **水分管理**：土壤湿度是否适宜该作物生长，是否需要增加或减少灌溉频率？如何确保根系的健康？\n" +
            "   - **光照管理**：该作物的光照需求如何？是否需要调整光照强度或改变种植密度，以确保作物充分接受阳光？\n" +
            "   - **环境湿度与CO2浓度**：如何调节环境湿度和CO2浓度来优化作物的光合作用和整体生长？\n" +
            "3. 针对该环境数据，提供如何调整这些环境参数（温度、湿度、光照等），并给出具体操作步骤，确保作物达到最佳生长条件。\n" +
            "4. 针对该作物，提供其他养护建议，包括但不限于：\n" +
            "   - **施肥管理**：建议的施肥类型和频率，如何根据作物的生长阶段合理施肥？\n" +
            "   - **病虫害防治**：常见的病虫害预防措施，如何通过农业操作（如轮作、间作等）减少病虫害的发生？\n" +
            "   - **土壤改良与管理**：如何通过土壤改良措施（如有机肥、微生物施用等）来提升土壤肥力和健康？\n" +
            "5. 在不同的生长阶段，针对该作物提供不同的养护策略，例如苗期、开花期、果实生长期的不同养护需求。\n" +
            "6. 提供一些长期的农业管理建议，帮助作物在多季节的变化中持续健康生长，减少作物的气候依赖性。\n\n" +
            "请确保您的回答详细、专业、易于操作，并为农户提供可行的实施方案。";


    private static final String HEALTHY_ADVICE = "作为一名植物病理学专家，请根据以下信息，为作物健康管理提供深度分析和专业建议,输出限制在500个汉字以内：\n" +
            "1. 作物名称：%s\n" +
            "2. 作物种植区域：%s\n" +
            "3. 当前季节：%s\n" +
            "结合当前季节和作物生长周期，分析该作物在该地区易受哪些典型病害的影响，并提供如下内容：\n" +
            "1. 基于地理位置与气候变化，可能存在的病害类型及其发生的季节性规律。\n" +
            "2. 针对这些病害，请提供具体的预防与防治建议，包括农业操作、化学/生物防治的最佳实践及其实施时机。\n" +
            "3. 以科学依据为基础，提出如何通过优化土壤管理、水分控制、施肥方案等措施增强作物的抗病性。";


    private static final String DISEASE_ADVICE = "您是专业的植物病理学专家，请根据以下作物病害信息提供详细的病害诊断与应急处理方案,输出限制在500个汉字以内：\n" +
            "1. 作物名称：%s\n" +
            "2. 作物种植区域：%s\n" +
            "3. 当前季节：%s\n" +
            "4. 已诊断病害：%s\n" +
            "根据以上信息，您需要提供以下专业分析与建议：\n" +
            "1. 请基于病害的生命周期、症状及作物生长状态，评估当前病害的严重性并分析是否需立即采取彻底清除感染部分的措施，或可通过药剂/生物防治手段进行控制。\n" +
            "2. 如果作物受感染部分仅限于局部，您认为应采取哪些隔离措施，如何有效防止病害蔓延并保护健康部分？\n" +
            "3. 请建议针对该病害，哪些早期诊断方法可有效识别其初期症状，确保能够及时采取处理措施。\n" +
            "4. 针对该作物的长期抗病能力建设，您有哪些推荐的土壤健康管理、作物轮作与栽培方式来降低未来相同病害发生的概率？";

    private static final String PLANT_IDENTIFICATION = "请作为专业植物学家和园艺专家，执行以下任务：\n" +
            "1.图像分析\n" +
            "识别图中植物的【学名】及【常见中文名称】\n" +
            "标注关键特征（如叶片形状/花序类型/茎干形态等）\n" +
            "判断生长阶段（幼苗/成熟/开花期等）\n" +
            "2.养护指南\n" +
            "光照需求：每日推荐光照时长及强度（如\"耐阴/需全日照\"）\n" +
            "浇水建议：频率、水量及注意事项（如\"见干见湿/保持湿润\"）\n" +
            "土壤要求：推荐土壤类型及pH值\n" +
            "温湿度：适宜生长范围及越冬建议\n" +
            "施肥方案：肥料类型及施肥频率\n" +
            "3.问题诊断\n" +
            "如果发现叶片黄化/枯萎等异常现象，提供可能原因及解决方案\n" +
            "4.扩展知识\n" +
            "该植物的原生地及有趣冷知识\n" +
            "是否对宠物/儿童有毒害风险\n" +
            "\n" +
            "请用结构化格式回复，优先使用emoji图标分类（\uD83C\uDF1E光照/\uD83D\uDCA7浇水等）。";

    // 通用方法：根据用户列表构造 JSON 数组
    private JSONArray buildUserJsonArray(List<UserYouMayKnowDTO> users) throws JSONException {
        JSONArray array = new JSONArray();
        for (UserYouMayKnowDTO dto : users) {
            JSONObject obj = new JSONObject();
            obj.put("id", dto.getId());
            // 根据购买状态生成描述性字段
            String deviceStatus = dto.getIsPurchased()
                    ? "已购买设备，种植的植物：" + (dto.getPlantsName() != null ? dto.getPlantsName() : Collections.emptyList())
                    : "未购买设备，暂未种植绿植";
            obj.put("deviceStatus", deviceStatus);
            obj.put("bio", dto.getBio());
            obj.put("message", dto.getMessage());
            obj.put("plantsName", dto.getPlantsName() != null ? dto.getPlantsName() : Collections.emptyList());
            array.put(obj);
        }
        return array;
    }


    /**
     * 获取AccessToken
     *
     * @return
     */
    public String getAccessToken() throws Exception {
        String url = "https://aip.baidubce.com/oauth/2.0/token?" +
                "grant_type=client_credentials&" +
                "client_id=" + API_KEY + "&" +
                "client_secret=" + SECRET_KEY;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(true);

        // 读取响应
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        // 解析返回的JSON数据
        JSONObject jsonResponse = new JSONObject(response.toString()); // 使用 org.json.JSONObject 解析
        String accessToken = jsonResponse.getString("access_token"); // 获取 access_token 字段
        log.info("access_token: {}", accessToken);
        return accessToken;
    }

    /**
     * 发送问答请求
     *
     * @param accessToken
     * @param messages
     * @return
     * @throws Exception
     */
    public String callBaidu(String accessToken, JSONArray messages, Integer type) throws Exception {
        log.info("发送请求:{}", messages);
        String baseUrl;
        if (type == 1) { // 文本
            baseUrl = "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions";
        } else { // 多模态
            baseUrl = "https://qianfan.baidubce.com/v2/chat/completions";
        }

        // 创建 RestTemplate 实例
        RestTemplate restTemplate = new RestTemplate();

        // 构造请求体
        org.json.JSONObject payload = new org.json.JSONObject();
        payload.put("messages", messages);
        payload.put("model", "deepseek-vl2");
        //log.info("payload: {}", payload);

        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 添加Bearer Token认证
        headers.set("Authorization", "Bearer " + Multimodal_Api_Key);

        // 创建请求实体
        HttpEntity<String> entity = new HttpEntity<>(payload.toString(), headers);

        // 发送 POST 请求
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "?access_token=" + accessToken,
                HttpMethod.POST,
                entity,
                String.class
        );
        log.info("返回响应:{}", response.getBody());

        return response.getBody(); // 返回响应体
    }

    /**
     * 生成多模态消息
     *
     * @param imageUrls
     * @param prompt
     * @return
     */
    private static JSONObject generateMutlMessage(List<String> imageUrls, String prompt) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", "user");

        // 构造content数组，包含文本和图片
        JSONArray contentArray = new JSONArray();
        // 添加文本部分
        JSONObject textContent = new JSONObject();
        textContent.put("type", "text");
        textContent.put("text", prompt);

        // 放入文本内容
        contentArray.put(textContent);

        // 添加图片部分
        if (imageUrls != null) {
            for (String url : imageUrls) {
                JSONObject imageContent = new JSONObject();
                imageContent.put("type", "image_url");

                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", url);

                imageContent.put("image_url", imageUrl);
                contentArray.put(imageContent);
            }
        }
        message.put("content", contentArray);
        return message;
    }

    /**
     * 识别植物
     *
     * @param imgUrl
     * @return
     */
    public String identifyPlant(String imgUrl) throws Exception {
        JSONArray messages = new JSONArray();
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(imgUrl);
        JSONObject message = generateMutlMessage(imageUrls, PLANT_IDENTIFICATION);
        messages.put(message);
        // 获取 Access Token
        String accessToken = getAccessToken();

        // 调用百度文心一言 API
        return callBaidu(accessToken, messages, 0);
    }


    /**
     * 生成文本消息
     *
     * @param prompt
     * @return
     * @throws JSONException
     */
    private static JSONObject generateTextMessage(String prompt) throws JSONException {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);

        return message;
    }


    /**
     * 流式回答
     *
     * @param accessToken
     * @param messages
     * @param chunkConsumer
     * @throws Exception
     */
    public CompletableFuture<Void> callBaiduStream(String accessToken, JSONArray messages, Consumer<String> chunkConsumer) throws Exception {
        log.info("发送流式请求:{}", messages);
        CompletableFuture<Void> future = new CompletableFuture<>();
        JSONObject payload = new JSONObject();
        payload.put("messages", messages);
        payload.put("stream", true);

        WebClient webClient = WebClient.builder()
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 增加内存缓冲区
                .build();
        val st = System.currentTimeMillis();
        webClient.post()
                .uri("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions?access_token=" + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMinutes(5)) // 超时控制
                .doOnError(error -> log.error("流式请求失败", error))
                .doFinally(signal -> log.info("流式请求完成，信号: {}", signal))
                .subscribe(
                        chunk -> {
                            try {
                                JSONObject json = new JSONObject(chunk);
                                if (json.has("result")) {
                                    String result = json.getString("result");
                                    // 替换\n为\\n
                                    String logContent = result.replace("\n", "\\n");
                                    //log.info("ai回答: {}", logContent);
                                    chunkConsumer.accept(logContent);
                                }
                            } catch (Exception e) {
                                log.error("解析流式响应失败", e);
                                future.completeExceptionally(e);
                                throw new RuntimeException("解析失败", e);
                            }
                        },
                        error -> {
                            log.error("流式请求订阅失败", error);
                            future.completeExceptionally(error);
                            throw new RuntimeException("请求失败", error);
                        },
                        () -> {
                            future.complete(null); // 流正常结束时完成future
                            log.info("回答耗时:{}ms", System.currentTimeMillis() - st);
                        }
                );
        return future;
    }


    public String recommendFollowees(
            List<UserYouMayKnowDTO> followedUsers,
            List<UserYouMayKnowDTO> randomUsers
    ) throws Exception {
        // 1. 把最后一个元素当作用户本人，其余是真正已关注的用户

        if (followedUsers == null || followedUsers.isEmpty()) {
            throw new IllegalArgumentException("followedUsers 列表不能为空且至少包含一个元素（用户本人）");
        }

        UserYouMayKnowDTO userSelf = followedUsers.get(followedUsers.size() - 1);
        List<UserYouMayKnowDTO> actualFollowed = new ArrayList<>(followedUsers);
        actualFollowed.remove(actualFollowed.size() - 1);

        // 在推荐方法中调用：
        // 2. 构造 JSON 数组：已关注用户
        JSONArray followedArray = buildUserJsonArray(actualFollowed);

        // 3. 构造 JSON 数组：随机备选用户
        JSONArray randomArray = buildUserJsonArray(randomUsers);

        // 4. 用户本人购买/种植状态
        String plantNames = userSelf.getPlantsName() != null ? userSelf.getPlantsName() : "[]";
        String userStatus = userSelf.getIsPurchased()
                ? "已购买设备，种植的植物：" + plantNames
                : "未购买设备，暂未种植绿植";

        // 5. 构造 LLM Prompt（更详细的描述，强调设备和种植经历的重要性）
        String prompt = String.format(
                "你是一位基于大语言模型的智能推荐系统，\n" +
                        "请依据以下信息，为该用户推荐20位(不足20位直接返回全部用户id即可)用户（只返回ID列表）：\n\n" +
                        "1. 用户本人信息：%s\n" +
                        "2. 用户已关注的人：%s\n" +
                        "3. 随机备选用户：%s\n\n" +
                        "输出格式：\n" +
                        "[\"id1\",\"id2\",...]\n" +
                        "**从随机备选用户以及用户已关注的人中，为该用户推荐20位(注意数量一定要等于20个)用户**" +
                        "不要返回任何多余文字。请注意排除掉用户本人信息！",
                new JSONObject()
                        .put("id", userSelf.getId())
                        .put("isPurchased", userSelf.getIsPurchased())
                        .put("bio", userSelf.getBio())
                        .put("message", userSelf.getMessage())
                        .put("plantsName", userSelf.getPlantsName())
                        .put("userStatus", userStatus),
                followedArray.toString(),
                randomArray.toString()
        );

        // 6. 调用 LLM 接口
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        String accessToken = getAccessToken();
        return callBaidu(accessToken, messages, 1);

    }


    public String recommendPosts(List<PostSummaryDTO> userViewLogs, List<PostSummaryDTO> postsInfo, UserPurchaseInfoDTO userPurchaseInfo, boolean isPusrchased) throws Exception {

        // 1. 构造用户浏览记录的 JSON 数组
        JSONArray browsedArray = new JSONArray();
        for (PostSummaryDTO dto : userViewLogs) {
            JSONObject obj = new JSONObject();
            obj.put("postId", dto.getPostId());
            obj.put("title", dto.getTitle());
            obj.put("totalComments", dto.getTotalComments());
            obj.put("totalCollects", dto.getTotalCollects());
            obj.put("totalViews", dto.getTotalViews());
            obj.put("totalLikes", dto.getTotalLikes());
            obj.put("userViewTotal", dto.getUserViewTotal());
            obj.put("viewTime", dto.getViewTime().toString());
            browsedArray.put(obj);
        }

        // 2. 构造所有帖子详情的 JSON 数组
        JSONArray allPostsArray = new JSONArray();
        for (PostSummaryDTO dto : postsInfo) {
            JSONObject obj = new JSONObject();
            obj.put("postId", dto.getPostId());
            obj.put("title", dto.getTitle());
            obj.put("totalComments", dto.getTotalComments());
            obj.put("totalCollects", dto.getTotalCollects());
            obj.put("totalViews", dto.getTotalViews());
            obj.put("totalLikes", dto.getTotalLikes());
            obj.put("userViewTotal", dto.getUserViewTotal());
            obj.put("viewTime", dto.getViewTime().toString());
            allPostsArray.put(obj);
        }

        // 3. 用户购买情况描述
        String plantNames = userPurchaseInfo.getPlantNames() != null ? userPurchaseInfo.getPlantNames().toString() : "null";
        String deviceStatus = isPusrchased
                ? "已购买设备，请多推荐植物养护类文章，其种植的植物是：" + plantNames
                : "未购买设备，暂未种植植物，请多推荐非种植类的文章";

        // 4. 构造 Prompt，包含完整 JSON 信息
        String prompt = String.format(
                "你是一位基于大语言模型的智能推荐系统，\n" +
                        "1. 用户已浏览过的帖子详情：%s\n" +
                        "2. 论坛中所有可选帖子的详情：%s\n" +
                        "3. 用户购买情况：%s\n\n" +
                        "请基于以上信息，为该用户推荐32个（注意数量一定要等于32）的帖子ID。\n" +
                        "只返回一个 JSON 数组，像这样：\n" +
                        "[\"id1\",\"id2\",\"id3\",...]\n" +
                        "不要返回任何多余的文字。",
                browsedArray.toString(),
                allPostsArray.toString(),
                deviceStatus
        );

        log.info("提示信息：\n{}", prompt);

        // 5. 调用 LLM 接口
        JSONArray messages = new JSONArray();
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);
        messages.put(userMsg);

        String accessToken = getAccessToken();
        return callBaidu(accessToken, messages, 1);
    }


}
