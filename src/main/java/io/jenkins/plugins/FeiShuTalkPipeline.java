package io.jenkins.plugins;

import com.alibaba.fastjson2.JSON;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import io.jenkins.plugins.enums.MsgTypeEnum;
import io.jenkins.plugins.model.ButtonModel;
import io.jenkins.plugins.model.MessageModel;
import io.jenkins.plugins.service.impl.FeiShuTalkServiceImpl;
import io.jenkins.plugins.tools.Logger;
import io.jenkins.plugins.tools.Utils;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.List;
import java.util.Map;

/**
 * 支持 pipeline 中使用
 *
 * <p>* 不要使用 @Data 注解，spotbugs 会报错 *
 *
 * <p>* Redundant nullcheck of this$title, which is known to be non-null in *
 * io.jenkins.plugins.model.MessageModel.equals(Object)
 *
 * @author xm.z
 */
@Getter
@Setter
@SuppressWarnings("unused")
public class FeiShuTalkPipeline extends Builder implements SimpleBuildStep {

    /**
     * 机器人 id
     */
    private String robot;

    /**
     * 消息类型
     */
    private MsgTypeEnum type;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 文本字符串
     */
    private List<String> text;

    /**
     * 群名片ID
     */
    private String shareChatId;

    /**
     * 图片KEY
     */
    private String imageKey;

    /**
     * 富文本消息体
     */
    private List<List<Map<String, String>>> post;

    /**
     * 按钮列表
     */
    private List<ButtonModel> buttons;

    private String rootPath = Jenkins.get().getRootUrl();

    private FeiShuTalkServiceImpl service = new FeiShuTalkServiceImpl();

    @DataBoundConstructor
    public FeiShuTalkPipeline(String robot) {
        this.robot = robot;
    }

    @DataBoundSetter
    public void setType(MsgTypeEnum type) {
        if (type == null) {
            type = MsgTypeEnum.TEXT;
        }
        this.type = type;
    }

    @DataBoundSetter
    public void setTitle(String title) {
        this.title = title;
    }

    @DataBoundSetter
    public void setText(List<String> text) {
        this.text = text;
    }

    @DataBoundSetter
    public void setShareChatId(String shareChatId) {
        this.shareChatId = shareChatId;
    }

    @DataBoundSetter
    public void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }

    @DataBoundSetter
    public void setPost(List<List<Map<String, String>>> post) {
        this.post = post;
    }

    @DataBoundSetter
    public void setButtons(List<ButtonModel> buttons) {
        this.buttons = buttons;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace,
                        @NonNull EnvVars envVars, @NonNull Launcher launcher, @NonNull TaskListener listener) {

        boolean defaultButtons = MsgTypeEnum.INTERACTIVE.equals(type) && (buttons == null || buttons.isEmpty());

        if (defaultButtons) {
            String jobUrl = rootPath + run.getUrl();
            this.buttons = Utils.createDefaultButtons(jobUrl);
        } else if (buttons != null) {
            buttons.forEach(item -> {
                item.setTitle(envVars.expand(item.getTitle()));
                item.setUrl(envVars.expand(item.getUrl()));
            });
        }

        MessageModel messageModel = MessageModel.builder().type(type).title(envVars.expand(title))
                .text(envVars.expand(buildText())).buttons(buttons).build();

        String result = service.send(envVars.expand(robot), messageModel);
        if (!StringUtils.isEmpty(result)) {
            Logger.error(listener, result);
        }
    }

    private String buildText() {
        switch (type) {
            case IMAGE:
                return imageKey;
            case SHARE_CHAT:
                return shareChatId;
            case POST:
                return JSON.toJSONString(post);
            default:
                return Utils.join(text);
        }
    }

    @Symbol({"feishutalk", "feiShuTalk"})
    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        @NonNull
        @Override
        public String getDisplayName() {
            return "FeiShuTalk";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> t) {
            return false;
        }
    }

}
