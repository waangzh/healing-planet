package com.example.demos.web.mcp;

import com.example.demos.web.common.enums.WifiStatus;
import com.example.demos.web.pojo.dto.DeviceDto;
import com.example.demos.web.pojo.entity.Device;
import com.example.demos.web.service.DeviceService;
import com.example.demos.web.utils.SpringContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.*;

@ServerEndpoint("/mcp")
@Component
public class McpServerEndpoint {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(McpServerEndpoint.class);

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("MCP client connected: " + session.getId());
    }

    @OnMessage
    public void onMessage(Session session, String message) throws IOException {
        JsonNode request = mapper.readTree(message);
        String method = request.get("method").asText();
        JsonNode idNode = request.get("id");
        Object id = null;
        if (idNode != null) {
            if (idNode.isInt()) {
                id = idNode.intValue();
            } else if (idNode.isTextual()) {
                id = idNode.textValue();
            }
        }

        Map<String, Object> result = new HashMap<>();

        try {
            switch (method) {
                case "initialize":
                    Map<String, Object> capabilities = new LinkedHashMap<>();

                    // 标准服务器能力
                    capabilities.put("logging", new HashMap<>());
                    Map<String, Object> promptsCaps = new HashMap<>();
                    promptsCaps.put("listChanged", true);
                    capabilities.put("prompts", promptsCaps);

                    Map<String, Object> resourcesCaps = new HashMap<>();
                    resourcesCaps.put("subscribe", true);
                    resourcesCaps.put("listChanged", true);
                    capabilities.put("resources", resourcesCaps);

                    Map<String, Object> toolsCaps = new HashMap<>();
                    toolsCaps.put("listChanged", true);
                    capabilities.put("tools", toolsCaps);

                    Map<String, Object> serverInfo = new LinkedHashMap<>();
                    serverInfo.put("name", "local-mcp");
                    serverInfo.put("version", "1.0.0");

                    Map<String, Object> resultOrdered = new LinkedHashMap<>();
                    resultOrdered.put("protocolVersion", "2024-11-05");
                    resultOrdered.put("capabilities", capabilities);
                    resultOrdered.put("serverInfo", serverInfo);
                    resultOrdered.put("instructions", "Optional instructions for the client");

                    result = resultOrdered;

                    //Map<String, Object> notify = new HashMap<>();
                    //notify.put("jsonrpc", "2.0");
                    //notify.put("method", "notifications/initialized");
                    //session.getBasicRemote().sendText(mapper.writeValueAsString(notify));
                    break;

                case "ping":
                    // ping 直接返回
                    //result.put("pong", true);
                    break;

                case "notifications/initialized":
                    break;
                case "tools/list":
                    List<Map<String, Object>> tools = new ArrayList<>();

                    // getPotData
                    Map<String, Object> getPotData = new HashMap<>();
                    getPotData.put("name", "getPotData");
                    getPotData.put("description", "查询花盆的传感器数据（温度、湿度、土壤湿度等）");

                    Map<String, Object> getPotDataSchema = new HashMap<>();
                    getPotDataSchema.put("type", "object");
                    getPotDataSchema.put("properties", new HashMap<String, Object>());
                    getPotData.put("inputSchema", getPotDataSchema); // 改成 inputSchema

                    tools.add(getPotData);

                    // getSwitchStates
                    Map<String, Object> getSwitchStates = new HashMap<>();
                    getSwitchStates.put("name", "getSwitchStates");
                    getSwitchStates.put("description", "查询花盆所有开关的状态（如水泵、补光灯）");

                    Map<String, Object> getSwitchStatesSchema = new HashMap<>();
                    getSwitchStatesSchema.put("type", "object");
                    getSwitchStatesSchema.put("properties", new HashMap<String, Object>());
                    getSwitchStates.put("inputSchema", getSwitchStatesSchema);

                    tools.add(getSwitchStates);

                    // setSwitchState
                    Map<String, Object> setSwitchState = new HashMap<>();
                    setSwitchState.put("name", "setSwitchState");
                    setSwitchState.put("description", "批量设置花盆的开关状态（风扇、水泵、补光灯、WiFi等）");

                    Map<String, Object> setSwitchStateSchema = new HashMap<>();
                    setSwitchStateSchema.put("type", "object");

                    Map<String, Object> setSwitchStateProps = new HashMap<>();

                    Map<String, Object> fanSwitchProp = new HashMap<>();
                    fanSwitchProp.put("type", "integer");
                    fanSwitchProp.put("enum", Arrays.asList(0, 1));
                    fanSwitchProp.put("description", "风扇开关（0=关，1=开）");
                    setSwitchStateProps.put("fanSwitch", fanSwitchProp);

                    Map<String, Object> pumpProp = new HashMap<>();
                    pumpProp.put("type", "integer");
                    pumpProp.put("enum", Arrays.asList(0, 1));
                    pumpProp.put("description", "灌溉水泵开关（0=关，1=开）");
                    setSwitchStateProps.put("irriogationPumpStatus", pumpProp);

                    Map<String, Object> lightProp = new HashMap<>();
                    lightProp.put("type", "integer");
                    lightProp.put("enum", Arrays.asList(0, 1));
                    lightProp.put("description", "补光灯开关（0=关，1=开）");
                    setSwitchStateProps.put("lightStatus", lightProp);

                    setSwitchStateSchema.put("properties", setSwitchStateProps);
                    setSwitchStateSchema.put("required", Arrays.asList("fanSwitch", "irriogationPumpStatus", "lightStatus"));
                    setSwitchStateSchema.put("additionalProperties", false);

                    setSwitchState.put("inputSchema", setSwitchStateSchema);

                    tools.add(setSwitchState);

                    // 最终返回 result
                    Map<String, Object> toolsResult = new HashMap<>();
                    toolsResult.put("tools", tools);
                    toolsResult.put("nextCursor", null);

                    result = toolsResult;
                    break;

                case "tools/call":
                    String tool = request.get("params").get("name").asText();
                    JsonNode args = request.get("params");

                    DeviceService deviceService = SpringContext.getBean(DeviceService.class);
                    Device device = new Device();
                    device.setName("ESP8266");
                    List<Float> deviceInfo = deviceService.getLatestValue(device);

                    String textResult = "";

                    if ("getPotData".equals(tool)) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("soilMoisture", deviceInfo.get(0));
                        data.put("lightLux", deviceInfo.get(2));
                        data.put("co2", deviceInfo.get(1));
                        data.put("temperature", deviceInfo.get(3));
                        data.put("humidity", deviceInfo.get(4));

                        textResult = "花盆传感器数据：\n" +
                                "土壤湿度：" + data.get("soilMoisture") + "% \n" +
                                "光照强度：" + data.get("lightLux") + "lux \n" +
                                "CO₂：" + data.get("co2") + "% \n" +
                                "温度：" + data.get("temperature") + "℃ \n" +
                                "湿度：" + data.get("humidity") +"%";

                    } else if ("getSwitchStates".equals(tool)) {
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("IrrigationPumpStatus", deviceInfo.get(5).intValue());
                        data.put("LightStatus", deviceInfo.get(6).intValue());
                        data.put("FanSwitch", deviceInfo.get(7).intValue());

                        textResult = "花盆开关状态：\n" +
                                "灌溉水泵：" + data.get("IrrigationPumpStatus") + "\n" +
                                "补光灯：" + data.get("LightStatus") + "\n" +
                                "风扇：" + data.get("FanSwitch");

                    } else if ("setSwitchState".equals(tool)) {
                        try {
                            JsonNode argumentsNode = args.get("arguments"); // 取出 arguments
                            DeviceDto deviceDto = mapper.treeToValue(argumentsNode, DeviceDto.class);
                            deviceDto.setName("ESP8266");
                            deviceDto.setId(3);
                            deviceDto.setWifiSwitch(WifiStatus.ONLINE);
                            log.info("设置开关状态:{}", deviceDto);

                            deviceService.setSwitch(deviceDto);

                            textResult = "开关状态已更新";
                        } catch (Exception e) {
                            textResult = "发生错误: " + e.getMessage();
                        }
                    }

                    // MCP 标准格式返回
                    Map<String, Object> contentItem = new HashMap<>();
                    contentItem.put("type", "text");
                    contentItem.put("text", textResult);

                    Map<String, Object> resultWrapper = new LinkedHashMap<>();
                    resultWrapper.put("content", Collections.singletonList(contentItem));
                    resultWrapper.put("isError", false);

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("jsonrpc", "2.0");
                    response.put("id", id);
                    response.put("result", resultWrapper);

                    session.getBasicRemote().sendText(mapper.writeValueAsString(response));
                    return;

                default:
                    Map<String, Object> error = new HashMap<>();
                    error.put("code", -32601);
                    error.put("message", "Method not found");

                    Map<String, Object> responses = new HashMap<>();
                    responses.put("jsonrpc", "2.0");
                    responses.put("id", id);
                    responses.put("error", error);

                    session.getBasicRemote().sendText(mapper.writeValueAsString(responses));
                    return;

            }

            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result);

            session.getBasicRemote().sendText(mapper.writeValueAsString(response));

        } catch (Exception e) {
            e.printStackTrace();

            Map<String, Object> error = new HashMap<>();
            error.put("code", -32603);
            error.put("message", e.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("error", error);

            session.getBasicRemote().sendText(mapper.writeValueAsString(response));
        }

    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("MCP client disconnected: " + session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }
}
