package io.jenkins.plugins;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.*;
import hudson.model.Cause.RemoteCause;
import hudson.model.Cause.UpstreamCause;
import hudson.model.Cause.UserIdCause;
import hudson.model.listeners.RunListener;
import io.jenkins.plugins.enums.BuildStatusEnum;
import io.jenkins.plugins.enums.MsgTypeEnum;
import io.jenkins.plugins.enums.NoticeOccasionEnum;
import io.jenkins.plugins.model.BuildJobModel;
import io.jenkins.plugins.model.MessageModel;
import io.jenkins.plugins.model.RunUser;
import io.jenkins.plugins.sdk.entity.support.Button;
import io.jenkins.plugins.service.impl.FeiShuTalkServiceImpl;
import io.jenkins.plugins.tools.JsonUtils;
import io.jenkins.plugins.tools.Logger;
import io.jenkins.plugins.tools.Utils;
import jenkins.model.Jenkins;
import lombok.extern.log4j.Log4j;
import org.apache.commons.lang.exception.ExceptionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 所有项目触发
 *
 * @author xm.z
 */
@Log4j
@Extension
public class FeiShuTalkRunListener extends RunListener<Run<?, ?>> {

    private final FeiShuTalkServiceImpl service = new FeiShuTalkServiceImpl();

    private final String rootPath = Jenkins.get().getRootUrl();

    @Override
    public void onStarted(Run<?, ?> run, TaskListener listener) {
        this.send(run, listener, NoticeOccasionEnum.START);
    }

    @Override
    public void onCompleted(Run<?, ?> run, @NonNull TaskListener listener) {
        Result result = run.getResult();
        NoticeOccasionEnum noticeOccasion = getNoticeOccasion(result);
        this.send(run, listener, noticeOccasion);
    }

    private NoticeOccasionEnum getNoticeOccasion(Result result) {
        if (Result.SUCCESS.equals(result)) {

            return NoticeOccasionEnum.SUCCESS;
        }
        if (Result.FAILURE.equals(result)) {

            return NoticeOccasionEnum.FAILURE;
        }
        if (Result.ABORTED.equals(result)) {

            return NoticeOccasionEnum.ABORTED;
        }
        if (Result.UNSTABLE.equals(result)) {

            return NoticeOccasionEnum.UNSTABLE;
        }
        if (Result.NOT_BUILT.equals(result)) {

            return NoticeOccasionEnum.NOT_BUILT;
        }
        return null;
    }

    private BuildStatusEnum getBuildStatus(NoticeOccasionEnum noticeOccasion) {
        if (NoticeOccasionEnum.START.equals(noticeOccasion)) {
            return BuildStatusEnum.START;
        }

        if (NoticeOccasionEnum.SUCCESS.equals(noticeOccasion)) {
            return BuildStatusEnum.SUCCESS;
        }

        if (NoticeOccasionEnum.FAILURE.equals(noticeOccasion)) {
            return BuildStatusEnum.FAILURE;
        }
        if (NoticeOccasionEnum.ABORTED.equals(noticeOccasion)) {

            return BuildStatusEnum.ABORTED;
        }
        if (NoticeOccasionEnum.UNSTABLE.equals(noticeOccasion)) {

            return BuildStatusEnum.UNSTABLE;
        }
        if (NoticeOccasionEnum.NOT_BUILT.equals(noticeOccasion)) {

            return BuildStatusEnum.NOT_BUILT;
        }
        return null;
    }

    private void log(TaskListener listener, String formatMsg, Object... args) {
        FeiShuTalkGlobalConfig globalConfig = FeiShuTalkGlobalConfig.getInstance();
        boolean verbose = globalConfig.isVerbose();
        if (verbose) {
            // Logger.line(listener, LineType.START);
            Logger.debug(listener, "飞书插件：" + formatMsg, args);
            // Logger.line(listener, LineType.END);
        }
    }

    private RunUser getUser(Run<?, ?> run, TaskListener listener) {
        UserIdCause userIdCause = run.getCause(UserIdCause.class);
        // 执行人信息
        User user = null;
        String executorName = null;
        String executorMobile = null;
        if (userIdCause != null && userIdCause.getUserId() != null) {
            user = User.getById(userIdCause.getUserId(), false);
        }

        if (user == null) {
            RemoteCause remoteCause = run.getCause(RemoteCause.class);
            UpstreamCause streamCause = run.getCause(UpstreamCause.class);
            if (remoteCause != null) {
                executorName = "remote " + remoteCause.getAddr();
            } else if (streamCause != null) {
                executorName = "project " + streamCause.getUpstreamProject();
            }
            if (executorName == null) {
                log(listener, "未获取到构建人信息，将尝试从构建信息中模糊匹配。");
                executorName = run.getCauses().stream().map(Cause::getShortDescription)
                        .collect(Collectors.joining());
            }
        } else {
            executorName = user.getDisplayName();
            executorMobile = user.getProperty(FeiShuTalkUserProperty.class).getMobile();
            if (executorMobile == null) {
                log(listener, "用户【%s】暂未设置手机号码，请前往 %s 添加。", executorName,
                        user.getAbsoluteUrl() + "/configure");
            }
        }
        return RunUser.builder().name(executorName).mobile(executorMobile).build();
    }

    private EnvVars getEnvVars(Run<?, ?> run, TaskListener listener) {
        EnvVars envVars;
        try {
            envVars = run.getEnvironment(listener);
        } catch (InterruptedException | IOException e) {
            envVars = new EnvVars();
            log.error(e);
            log(listener, "获取环境变量时发生异常，将只使用 jenkins 默认的环境变量。");
            log(listener, ExceptionUtils.getStackTrace(e));
        }
        return envVars;
    }

    private boolean skip(TaskListener listener, NoticeOccasionEnum noticeOccasion,
                         FeiShuTalkNotifierConfig notifierConfig) {
        String stage = noticeOccasion.name();
        Set<String> noticeOccasions = notifierConfig.getNoticeOccasions();
        if (noticeOccasions.contains(stage)) {
            return false;
        }
        log(listener, "机器人 %s 已跳过 %s 环节", notifierConfig.getRobotName(), stage);
        return true;
    }

    private void send(Run<?, ?> run, TaskListener listener, NoticeOccasionEnum noticeOccasion) {
        Job<?, ?> job = run.getParent();
        FeiShuTalkJobProperty property = job.getProperty(FeiShuTalkJobProperty.class);

        if (property == null) {
            this.log(listener, "不支持的项目类型，已跳过");
            return;
        }

        // 执行人信息
        RunUser user = getUser(run, listener);

        // 项目信息
        String projectName = job.getFullDisplayName();
        String projectUrl = job.getAbsoluteUrl();

        // 构建信息
        BuildStatusEnum statusType = getBuildStatus(noticeOccasion);
        String jobName = run.getDisplayName();
        String jobUrl = rootPath + run.getUrl();
        String duration = run.getDurationString();
        List<Button> buttons = Utils.createDefaultButtons(jobUrl);
        List<String> result = new ArrayList<>();
        List<FeiShuTalkNotifierConfig> notifierConfigs = property.getCheckedNotifierConfigs();

        // 环境变量
        EnvVars envVars = getEnvVars(run, listener);

        for (FeiShuTalkNotifierConfig item : notifierConfigs) {
            boolean skipped = skip(listener, noticeOccasion, item);
            if (skipped) {
                continue;
            }

            String robotId = item.getRobotId();
            String content = item.getContent();
            boolean atAll = item.isAtAll();
            Set<String> atOpenIds = item.getAtOpenIds();

            BuildJobModel buildJobModel = BuildJobModel.builder().projectName(projectName)
                    .projectUrl(projectUrl).jobName(jobName).jobUrl(jobUrl)
                    .statusType(statusType).duration(duration).executorName(user.getName())
                    .executorMobile(user.getMobile()).content(envVars.expand(content).replaceAll("\\\\n", "\n"))
                    .build();

            String statusLabel = statusType == null ? "unknown" : statusType.getLabel();

            MessageModel message = MessageModel.builder().type(MsgTypeEnum.INTERACTIVE)
                    .atAll(atAll).atOpenIds(atOpenIds).title(String.format("%s %s %s", "\uD83D\uDCE2", projectName, statusLabel))
                    .text(buildJobModel.toMarkdown()).buttons(buttons).build();

            log(listener, "当前机器人信息: %s", item.getRobotName());
            log(listener, "发送的消息详情: %s", JsonUtils.toJsonStr(message));

            String msg = service.send(robotId, message);

            if (msg != null) {
                result.add(msg);
            }
        }

        if (!result.isEmpty()) {
            result.forEach(msg -> Logger.error(listener, msg));
        }
    }
}
