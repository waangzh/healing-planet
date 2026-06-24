package com.example.demos.web.utils;

import com.example.demos.web.pojo.dto.EnvironmentDataDTO;
import com.example.demos.web.pojo.entity.EnvironmentData;
import org.json.JSONException;
import org.json.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@Slf4j
public class BaiDuUtil {
    private String API_KEY;
    private String SECRET_KEY;
    private String Multimodal_Api_Key;

    private static final String PLANT_TYPE = "识别图中植物";

    private static final String PROMPT_TEMPLATE = "您是一位专业的农业作物养护专家，请根据以下农业作物的环境数据，提供科学、详细且专业的养护方案，该方案限制在500字以内：\n\n" +
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

    private static final String ANALYIS_PROMPT = "您是一位专业的植物学家和养护顾问，请根据以下绿植的历史环境数据，提供科学、详细且专业的分析报告和养护方案，该方案限制在500字以内：\n" +
            "\n" +
            "1. **绿植名称**: %s\n" +
            "2. **历史环境数据的每日平均值**: %s（这里将嵌入绿植对应的历史环境数据，数据格式为列表）\n" +
            "\n" +
            "请基于上述历史数据，详细回答以下问题：\n" +
            "\n" +
            "1. **历史数据分析**:\n" +
            "   - **趋势分析**: 详细分析历史数据中，温度、湿度、光照等关键环境参数的长期变化趋势。是否存在季节性或周期性波动？这些波动对绿植的生长有何影响？\n" +
            "   - **异常事件识别**: 识别并分析历史数据中的异常峰值或谷值（例如，某段时间内温度骤降或光照突然增强）。这些异常事件可能对绿植造成了哪些潜在伤害？\n" +
            "   - **生长状况关联**: 结合历史环境数据，推断绿植在不同环境条件下的生长状况。例如，在光照充足、湿度适中的时期，绿植的生长是否更为旺盛？\n" +
            "\n" +
            "2. **养护建议与策略调整**:\n" +
            "   - **环境参数优化**: 基于历史数据分析结果，为绿植提供一套优化的环境参数设置。例如，在夏季高光照期，建议采取何种遮阳措施？在冬季低温期，如何进行温度和湿度的精准调控？\n" +
            "   - **浇水与施肥周期**: 根据历史土壤湿度的波动规律，提供一个更科学的浇水和施肥周期。例如，如果数据显示土壤湿度下降速度快，是否需要增加浇水频率？\n" +
            "   - **病虫害预防**: 基于历史数据的波动，预测绿植在哪些环境条件下（如高温高湿）更容易发生病虫害，并提供针对性的预防措施。\n" +
            "\n" +
            "3. **未来养护方案**:\n" +
            "   - **智能化管理**: 结合历史数据，提供一套智能化的养护管理建议。例如，如何利用自动化设备（如自动灌溉系统、环境监测传感器）来实时调整环境，以应对未来的环境波动？\n" +
            "   - **长期健康规划**: 提供一个长期的、跨季节的养护规划，帮助绿植在不同气候条件下持续健康生长，并减少因环境变化带来的养护挑战。\n" +
            "\n" +
            "请确保您的分析报告详细、专业、易于理解，并为绿植的主人提供可行的实施方案。";

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
     * @param type 1-文本/0-多模态
     * @return
     * @throws Exception
     */
    public String callBaidu(String accessToken, JSONArray messages,Integer type) throws Exception {
        log.info("发送请求:{}", messages);
        String baseUrl;
        if(type == 1){
            baseUrl ="https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions";
        }else{
            baseUrl ="https://qianfan.baidubce.com/v2/chat/completions";
        }

        // 创建 RestTemplate 实例
        RestTemplate restTemplate = new RestTemplate();

        // 构造请求体
        org.json.JSONObject payload = new org.json.JSONObject();
        payload.put("messages", messages);
        payload.put("model", "qwen3-vl-235b-a22b-thinking");
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
                baseUrl+ "?access_token=" + accessToken,
                HttpMethod.POST,
                entity,
                String.class
        );
        //log.info("返回响应:{}", response.getBody());

        return response.getBody(); // 返回响应体
    }

    /**
     * 生成养护建议
     *
     * @param plantName
     * @param temperature
     * @param soilMoisture
     * @param environmentHumidity
     * @param lightIntensity
     * @param co2Concentration
     * @return
     * @throws Exception
     */
    public String generatePlantCareAdvice(String plantName, Float temperature, Float soilMoisture,
                                          Float environmentHumidity, Float lightIntensity,
                                          Float co2Concentration, String location,
                                          List<String> imageUrls) throws Exception {
        log.info("生成prompt");
        // 生成 Prompt
        String prompt = String.format(
                PROMPT_TEMPLATE,
                plantName,
                temperature,
                soilMoisture,
                environmentHumidity,
                lightIntensity,
                co2Concentration,
                location
        );
        //String prompt = PLANT_TYPE;

        // 构造消息体
        JSONArray messages = new JSONArray();
        JSONObject message = generateMutlMessage(imageUrls, prompt);
        messages.put(message);

        // 获取 Access Token
        String accessToken = getAccessToken();

        // 调用百度文心一言 API
        return callBaidu(accessToken, messages,0);
    }

    /**
     * 生成多模态消息
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
     * 生成文本消息
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
     * 生成预防作物疾病措施
     *
     * @param healthy
     * @param diseaseName
     * @param position
     * @return
     */

    public String generatePreventDiseaseAdvice(Boolean healthy, String diseaseName, String position,
                                               String plantName) throws Exception {
        log.info("开始生成建议");
        //promote
        String promote = healthy ? String.format(
                HEALTHY_ADVICE,
                plantName,
                position,
                LocalDateTime.now().toString()
        ) : String.format(DISEASE_ADVICE,
                plantName,
                plantName,
                position,
                LocalDateTime.now().toString(),
                diseaseName
        );

        JSONArray messages = new JSONArray();
        JSONObject object = new JSONObject();
        object.put("role", "user");
        object.put("content", promote);
        messages.put(object);

        // 获取 Access Token
        String accessToken = getAccessToken();

        return callBaidu(accessToken, messages,1);
    }

    /**
     * ai写文章
     *
     * @param environmentDataDTOList
     * @param message
     * @return
     */
    public String writePost(List<EnvironmentDataDTO> environmentDataDTOList, String message) throws Exception {
        // 1. 构造用户浏览记录的 JSON 数组
        JSONArray jsonArrayrray = new JSONArray();
        for (EnvironmentDataDTO dto : environmentDataDTOList) {
            JSONObject obj = new JSONObject();
            obj.put("environmentData", dto.getDailyEnvironmentDataVO());
            obj.put("plantName", dto.getPlantName());
            jsonArrayrray.put(obj);
        }

        // 4. 构造 Prompt，包含完整 JSON 信息
        String prompt = String.format(
                "你是一位基于大语言模型的植物专家，我现在会给你一些信息，你需要根据以下信息，帮我去撰写一下今天的植物种植日志" +
                        "主要是一些心得体会，以及养护植物的注意事项，除非在下面的第一点中，用户提到了要什么样的风格，否则语调不要那么严肃，但是也要专业一些，像记日记一样，像把植物当成朋友一样来对待\n" +
                        "**1. 用户想提出的记录日志的要求：%s**\n" +
                        "**2. 该用户购买的植物的个数对应的个数%d，以及其对应的植物名字及其对应的环境数据%s**\n\n" +
                        "基于以上信息以及我上述提出的要求向我撰写日志，以markdown形式返回，区分一下标题和内容\n"+
                        "注意你给出的内容不需要包含任何时间天气信息,除非用户主动提出!"
                       , message,jsonArrayrray.length(),jsonArrayrray.toString()
        );

        log.info("提示信息：\n{}", prompt);

        JSONArray messages = new JSONArray();
        JSONObject object = new JSONObject();
        object.put("role", "user");
        object.put("content", prompt);
        messages.put(object);

        // 获取 Access Token
        String accessToken = getAccessToken();
        return callBaidu(accessToken, messages,1);

    }

    /**
     * 根据历史数据分析植物健康状况
     * @param plantName
     * @param environmentDataList
     * @param plantImg
     * @return
     */
    public String analyis(String plantName, List<Map<String, Object>> dailyDataList, String plantImg) throws Exception{
        // 生成 Prompt
        String prompt = String.format(
                ANALYIS_PROMPT,
                plantName,
                dailyDataList
        );
        log.info("使用analyis_prompt:{}",prompt);

        //String prompt = PLANT_TYPE;

        // 构造消息体
        JSONArray messages = new JSONArray();
        List<String> imageUrls = new ArrayList<>();
        imageUrls.add(plantImg);
        JSONObject message = generateMutlMessage(imageUrls, prompt);
        messages.put(message);

        // 获取 Access Token
        String accessToken = getAccessToken();

        // 调用百度文心一言 API
        return callBaidu(accessToken, messages,0);
    }
}
