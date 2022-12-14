package io.jenkins.plugins.model;

import io.jenkins.plugins.FeiShuTalkRobotConfig;
import io.jenkins.plugins.FeiShuTalkSecurityPolicyConfig;
import io.jenkins.plugins.enums.SecurityPolicyEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * 机器人配置信息
 *
 * @author xm.z
 */
@Data
@Slf4j
public class RobotConfigModel {

    /**
     * 关键字
     */
    private String keys;

    /**
     * 签名
     */
    private String sign;

    /**
     * api 接口
     */
    private String webhook;

    /**
     * 从机器人配置中解析元信息
     *
     * @param robotConfig 配置
     * @return 机器人配置
     */
    public static RobotConfigModel of(FeiShuTalkRobotConfig robotConfig) {
        List<FeiShuTalkSecurityPolicyConfig> securityPolicyConfigs = robotConfig.getSecurityPolicyConfigs();
        RobotConfigModel meta = new RobotConfigModel();
        meta.setWebhook(robotConfig.getWebhook());
        // 解析安全策略
        securityPolicyConfigs.stream()
                .filter(config -> StringUtils.isNotBlank(config.getValue()))
                .forEach(config -> {
                    String type = config.getType();
                    SecurityPolicyEnum securityPolicyEnum = SecurityPolicyEnum.valueOf(type);
                    switch (securityPolicyEnum) {
                        case KEY:
                            meta.setKeys(config.getValue());
                            break;
                        case SECRET:
                            meta.setSign(config.getValue());
                            break;
                        default:
                            log.error("对应的安全策略不存在：" + type);
                    }
                });
        return meta;
    }

    /**
     * 签名方法
     *
     * @return 签名
     */
    private static String createSign(long timestamp, String secret) {
        String result = "";
        try {
            String seed = timestamp + "\n" + secret;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signData = mac.doFinal(seed.getBytes(StandardCharsets.UTF_8));
            result = Base64.encodeBase64String(signData);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("飞书插件设置签名失败：", e);
        }
        return result;
    }

    public Map<String, Object> buildSign(Map<String, Object> content) {
        if (StringUtils.isNotBlank(sign)) {
            long timestamp = System.currentTimeMillis() / 1000L;
            content.put("timestamp", String.valueOf(timestamp));
            content.put("sign", createSign(timestamp, sign));
        }
        return content;
    }

}
