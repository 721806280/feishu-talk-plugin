package io.jenkins.plugins;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import io.jenkins.plugins.enums.NoticeOccasionEnum;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * FeiShuTalkNotifierConfig
 *
 * @author xm.z
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
public class FeiShuTalkNotifierConfig extends AbstractDescribableImpl<FeiShuTalkNotifierConfig> {

    private boolean checked;

    private String robotId;

    private String robotName;

    private boolean atAll;

    private String atOpenId;

    private String content;

    private Set<String> noticeOccasions;

    @DataBoundConstructor
    public FeiShuTalkNotifierConfig(boolean checked, String robotId, String robotName,
                                    boolean atAll, String atOpenId, String content, Set<String> noticeOccasions) {
        this.checked = checked;
        this.robotId = robotId;
        this.robotName = robotName;
        this.atAll = atAll;
        this.atOpenId = atOpenId;
        this.content = content;
        this.noticeOccasions = noticeOccasions;
    }

    public FeiShuTalkNotifierConfig(FeiShuTalkRobotConfig robotConfig) {
        this(false, robotConfig.getId(), robotConfig.getName(), false, null, null, getDefaultNoticeOccasions());
    }

    private static Set<String> getDefaultNoticeOccasions() {
        return FeiShuTalkGlobalConfig.getInstance().getNoticeOccasions();
    }

    public Set<String> getNoticeOccasions() {
        return noticeOccasions == null ? getDefaultNoticeOccasions() : noticeOccasions;
    }

    public Set<String> getAtOpenIds() {
        if (StringUtils.isEmpty(atOpenId)) {
            return new HashSet<>(16);
        }
        return Arrays.stream(StringUtils.split(atOpenId.replace("\n", ","), ","))
                .collect(Collectors.toSet());
    }

    public String getContent() {
        return content == null ? "" : content;
    }

    public void copy(FeiShuTalkNotifierConfig notifierConfig) {
        this.setChecked(notifierConfig.isChecked());
        this.setAtAll(notifierConfig.isAtAll());
        this.setAtOpenId(notifierConfig.getAtOpenId());
        this.setContent(notifierConfig.getContent());
        this.setNoticeOccasions(notifierConfig.getNoticeOccasions());
    }

    @Extension
    public static class FeiShuTalkNotifierConfigDescriptor extends Descriptor<FeiShuTalkNotifierConfig> {
        /**
         * ??????????????????
         *
         * @return ????????????
         */
        public NoticeOccasionEnum[] getNoticeOccasionTypes() {
            return NoticeOccasionEnum.values();
        }
    }
}
