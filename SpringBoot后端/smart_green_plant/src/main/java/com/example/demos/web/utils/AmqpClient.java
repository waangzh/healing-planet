package com.example.demos.web.utils;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demos.web.common.enums.FanStatus;
import com.example.demos.web.common.enums.LightStatus;
import com.example.demos.web.common.enums.PumpStatus;
import com.example.demos.web.common.properties.AliIoTConfigProperties;
import com.example.demos.web.mapper.DeviceMapper;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.pojo.entity.*;
import com.example.demos.web.mapper.EnvironmentDataMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.qpid.jms.JmsConnection;
import org.apache.qpid.jms.JmsConnectionListener;
import org.apache.qpid.jms.message.JmsInboundMessageDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;


@Service
@Slf4j
public class AmqpClient implements ApplicationRunner {
    private final static Logger logger = LoggerFactory.getLogger(AmqpClient.class);

    @Autowired
    private AliIoTConfigProperties aliIoTConfigProperties;
    @Autowired
    private EnvironmentDataMapper environmentDataMapper;
    @Autowired
    private PlantInstanceMapper plantInstanceMapper;
    @Autowired
    private DeviceMapper deviceMapper;
    //控制台服务端订阅中消费组状态页客户端ID一栏将显示clientId参数。
    
    //建议使用机器UUID、MAC地址、IP等唯一标识等作为clientId。便于您区分识别不同的客户端。
    private static String clientId;

    static {
        try {
            clientId = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    // 指定单个进程启动的连接数
    // 单个连接消费速率有限，请参考使用限制，最大64个连接
    // 连接数和消费速率及rebalance相关，建议每500QPS增加一个连接
    private static int connectionCount = 4;

    //业务处理异步线程池，线程池参数可以根据您的业务特点调整，或者您也可以用其他异步方式处理接收到的消息。
    @Autowired
    private ExecutorService executorService;

    public void start() throws Exception {
        List<Connection> connections = new ArrayList<>();

        //参数说明，请参见AMQP客户端接入说明文档。
        for (int i = 0; i < connectionCount; i++) {
            long timeStamp = System.currentTimeMillis();
            //签名方法：支持hmacmd5、hmacsha1和hmacsha256。
            String signMethod = "hmacsha1";

            //userName组装方法，请参见AMQP客户端接入说明文档。
            String userName = clientId + "-" + i + "|authMode=aksign"
                    + ",signMethod=" + signMethod
                    + ",timestamp=" + timeStamp
                    + ",authId=" + aliIoTConfigProperties.getAccessKeyId()
                    + ",iotInstanceId=" + aliIoTConfigProperties.getIotInstanceId()
                    + ",consumerGroupId=" + aliIoTConfigProperties.getConsumerGroupId()
                    + "|";
            //计算签名，password组装方法，请参见AMQP客户端接入说明文档。
            String signContent = "authId=" + aliIoTConfigProperties.getAccessKeyId() + "&timestamp=" + timeStamp;
            String password = doSign(signContent, aliIoTConfigProperties.getAccessKeySecret(), signMethod);
            String connectionUrl = "failover:(amqps://" + aliIoTConfigProperties.getHost() + ":5671?amqp.idleTimeout=80000)"
                    + "?failover.reconnectDelay=30";

            Hashtable<String, String> hashtable = new Hashtable<>();
            hashtable.put("connectionfactory.SBCF", connectionUrl);
            hashtable.put("queue.QUEUE", "default");
            hashtable.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.qpid.jms.jndi.JmsInitialContextFactory");
            Context context = new InitialContext(hashtable);
            ConnectionFactory cf = (ConnectionFactory) context.lookup("SBCF");
            Destination queue = (Destination) context.lookup("QUEUE");
            // 创建连接。
            Connection connection = cf.createConnection(userName, password);
            connections.add(connection);

            ((JmsConnection) connection).addConnectionListener(myJmsConnectionListener);
            // 创建会话。
            // Session.CLIENT_ACKNOWLEDGE: 收到消息后，需要手动调用message.acknowledge()。
            // Session.AUTO_ACKNOWLEDGE: SDK自动ACK（推荐）。
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            connection.start();
            // 创建Receiver连接。
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.setMessageListener(messageListener);
        }

        logger.info("amqp  is started successfully, and will exit after server shutdown ");
    }

    private MessageListener messageListener = message -> {
        try {
            //异步处理收到的消息，确保onMessage函数里没有耗时逻辑
            executorService.submit(() -> processMessage(message));
        } catch (Exception e) {
            logger.error("submit task occurs exception ", e);
        }
    };

    /**
     * 收到消息后进行处理
     */
    private void processMessage(Message message) {
        try {
            byte[] body = message.getBody(byte[].class);
            String content = new String(body);
            String topic = message.getStringProperty("topic");
            String messageId = message.getStringProperty("messageId");
            //logger.info("接收到消息,\n topic = {},\n messageId = {},\n content = {}",
            //        topic, messageId, content);
            //接收来自平台的数据，自动上传到数据库
            Content c = JSONUtil.toBean(content, Content.class);
            //硬件每发一个包含有数据的请求给阿里云物联网平台就存储接收到的数据
            if(c != null && c.getItems() != null) {
                Map<String, DeviceItems> items = c.getItems();
                EnvironmentData environmentData = new EnvironmentData();
                Device device = new Device();
                Integer plantInstanceId = deviceMapper.getPlantInstanceIdByName(c.getDeviceName());
                environmentData.setPlantInstanceId(plantInstanceId);
                log.info("接收到{}消息，实例id为{},内容:{}",c.getDeviceName(),plantInstanceId,c.getItems());
                device.setName(c.getDeviceName());
                device = deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getName,device.getName()));
                log.info("接收到数据，查询设备:{}",device);
                // 环境数据
                if(items.containsKey("EnvironmentHumidity")) {
                    environmentData.setHumidity(Double.parseDouble(items.get("EnvironmentHumidity").getValue()));
                }
                if(items.containsKey("SoilMoisture")) {
                    environmentData.setSoilMoisture(Double.parseDouble(items.get("SoilMoisture").getValue()));
                }
                if(items.containsKey("CO2Value")) {
                    environmentData.setCo2Concentration(Double.parseDouble(items.get("CO2Value").getValue()));
                }
                if(items.containsKey("Temperature")) {
                    environmentData.setTemperature(Double.parseDouble(items.get("Temperature").getValue()));
                }
                if(items.containsKey("LightLux")) {
                    environmentData.setLightIntensity(Double.parseDouble(items.get("LightLux").getValue()));
                }

                // 设备开关
                if (items.containsKey("FanSwitch")) {
                    device.setFanSwitch(
                            Double.parseDouble(items.get("FanSwitch").getValue()) == 1 ? FanStatus.ON:FanStatus.OFF);
                }
                if(items.containsKey("LightStatus")) {
                    device.setLightStatus(
                            Double.parseDouble(items.get("LightStatus").getValue()) == 1 ? LightStatus.ON:LightStatus.OFF);
                }
                if(items.containsKey("IrrigationPumpStatus")) {
                    device.setIrriogationPumpStatus(
                            Double.parseDouble(items.get("IrrigationPumpStatus").getValue()) == 1? PumpStatus.PUMP_ON:PumpStatus.PUMP_OFF
                    );
                }

                //绿植实例id，必须要传
                if(environmentData.getCo2Concentration()!=null && environmentData.getTemperature()!=null
                && environmentData.getHumidity()!=null && environmentData.getSoilMoisture()!=null
                && environmentData.getLightIntensity()!=null ){
                    // 记录时间
                    environmentData.setRecordedTime(LocalDateTime.now());
                    environmentDataMapper.insert(environmentData);
                    log.info("接收环境数据，并插入数据库:{}",environmentData);
                }
                if(device.getName()!=null && device.getFanSwitch()!=null
                        && device.getLightStatus()!=null && device.getIrriogationPumpStatus()!=null) {
                    log.info("更新设备开关:{}",device);
                    device.setTimestamp(LocalDateTime.now());
                    deviceMapper.updateById(device);
                }

            } else {
                logger.warn("平台传输的内容可能为空.");
            }
            //System.out.println("---------------------------------");
            //System.out.println(c);

            } catch (Exception e) {
            logger.error("加载数据错误", e);
        }
    }

    private JmsConnectionListener myJmsConnectionListener = new JmsConnectionListener() {
        /**
         * 连接成功建立。
         */
        @Override
        public void onConnectionEstablished(URI remoteURI) {
            logger.info("onConnectionEstablished, remoteUri:{}", remoteURI);
        }

        /**
         * 尝试过最大重试次数之后，最终连接失败。
         */
        @Override
        public void onConnectionFailure(Throwable error) {
            logger.error("onConnectionFailure, {}", error.getMessage());
        }

        /**
         * 连接中断。
         */
        @Override
        public void onConnectionInterrupted(URI remoteURI) {
            logger.info("onConnectionInterrupted, remoteUri:{}", remoteURI);
        }

        /**
         * 连接中断后又自动重连上。
         */
        @Override
        public void onConnectionRestored(URI remoteURI) {
            logger.info("onConnectionRestored, remoteUri:{}", remoteURI);
        }

        @Override
        public void onInboundMessage(JmsInboundMessageDispatch envelope) {
        }

        @Override
        public void onSessionClosed(Session session, Throwable cause) {
        }

        @Override
        public void onConsumerClosed(MessageConsumer consumer, Throwable cause) {
        }

        @Override
        public void onProducerClosed(MessageProducer producer, Throwable cause) {
        }
    };

    /**
     * 计算签名，password组装方法，请参见AMQP客户端接入说明文档。
     */
    private static String doSign(String toSignString, String secret, String signMethod) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(), signMethod);
        Mac mac = Mac.getInstance(signMethod);
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(toSignString.getBytes());
        return Base64.encodeBase64String(rawHmac);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        start();
    }
}