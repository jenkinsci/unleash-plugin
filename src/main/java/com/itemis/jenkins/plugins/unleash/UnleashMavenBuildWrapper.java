/*
 * The MIT License
 *
 * Copyright (c) 2009, NDS Group Ltd., James Nord, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.itemis.jenkins.plugins.unleash;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Model;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatcher;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.itemis.jenkins.plugins.unleash.util.MavenUtil;
import com.itemis.maven.plugins.unleash.util.MavenVersionUtil;
import com.itemis.maven.plugins.unleash.util.VersionUpgradeStrategy;

import hudson.Extension;
import hudson.Launcher;
import hudson.maven.AbstractMavenProject;
import hudson.maven.MavenModuleSet;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Item;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import hudson.util.RunList;
import hudson.util.Secret;
import net.sf.json.JSONObject;

/**
 * @author Stanley Hillner
 */
// This class was developed based on org.jvnet.hudson.plugins.m2release.M2ReleaseBuildWrapper
// The class still contains substantial parts of the original implementation
// original authors: James Nord & Dominik Bartholdi
public class UnleashMavenBuildWrapper extends BuildWrapper {
  private static String ENV_VAR_SCM_USERNAME = "UNLEASH_SCM_USERNAME";

  private static String ENV_VAR_SCM_PASSWORD = "UNLEASH_SCM_PASSWORD";

  private static String ENV_VAR_SCM_SSH_PASSPHRASE = "UNLEASH_SCM_SSH_PASSPHRASE";

  private static String ENV_VAR_SCM_SSH_PRIVATE_KEY = "UNLEASH_SCM_SSH_PRIVATE_KEY";

  private String goals = DescriptorImpl.DEFAULT_GOALS;

  private String profiles = DescriptorImpl.DEFAULT_PROFILES;

  private String releaseArgs = DescriptorImpl.DEFAULT_RELEASE_ARGS;

  private List<HookDescriptor> hooks = Lists.newArrayList();

  private boolean useLogTimestamps = DescriptorImpl.DEFAULT_USE_LOG_TIMESTAMPS;

  private String tagNamePattern = DescriptorImpl.DEFAULT_TAG_NAME_PATTERN;

  private String scmMessagePrefix = DescriptorImpl.DEFAULT_SCM_MESSAGE_PREFIX;

  private boolean preselectUseGlobalVersion = DescriptorImpl.DEFAULT_PRESELECT_USE_GLOBAL_VERSION;

  private boolean preselectAllowLocalReleaseArtifacts = DescriptorImpl.DEFAULT_PRESELECT_ALLOW_LOCAL_RELEASE_ARTIFACTS;

  private boolean preselectCommitBeforeTagging = DescriptorImpl.DEFAULT_PRESELECT_COMMIT_BEFORE_TAGGING;

  private String workflowPath = DescriptorImpl.DEFAULT_WORKFLOW_PATH;

  private String credentialsId;

  private int numberOfBuildsToLock = DescriptorImpl.DEFAULT_NUMBER_OF_LOCKED_BUILDS;

  private VersionUpgradeStrategy versionUpgradeStrategy = DescriptorImpl.DEFAULT_VERSION_UPGRADE_STRATEGY;

  @DataBoundConstructor
  public UnleashMavenBuildWrapper(String goals, String profiles, String releaseArgs, boolean useLogTimestamps,
      String tagNamePattern, String scmMessagePrefix, boolean preselectUseGlobalVersion, List<HookDescriptor> hooks,
      boolean preselectAllowLocalReleaseArtifacts, boolean preselectCommitBeforeTagging, String workflowPath,
      String credentialsId, int numberOfBuildsToLock, VersionUpgradeStrategy versionUpgradeStrategy) {
    super();
    this.goals = goals;
    this.profiles = profiles;
    this.releaseArgs = releaseArgs;
    this.useLogTimestamps = useLogTimestamps;
    this.tagNamePattern = tagNamePattern;
    this.scmMessagePrefix = scmMessagePrefix;
    this.preselectUseGlobalVersion = preselectUseGlobalVersion;
    this.hooks = hooks;
    this.preselectAllowLocalReleaseArtifacts = preselectAllowLocalReleaseArtifacts;
    this.preselectCommitBeforeTagging = preselectCommitBeforeTagging;
    this.workflowPath = workflowPath;
    this.credentialsId = credentialsId;
    this.numberOfBuildsToLock = numberOfBuildsToLock;
    this.versionUpgradeStrategy = MoreObjects.firstNonNull(versionUpgradeStrategy, VersionUpgradeStrategy.DEFAULT);
  }

  @Override
  public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) {
    if (!isReleaseBuild(build)) {
      return new Environment() {
        @Override
        public void buildEnvVars(Map<String, String> env) {
        }
      };
    }

    UnleashArgumentsAction arguments = build.getAction(UnleashArgumentsAction.class);
    StringBuilder command = new StringBuilder(getGoals());

    if (StringUtils.isNotBlank(this.workflowPath)) {
      // TODO handle absolute and relative paths!
      command.append(" -Dworkflow=").append(this.workflowPath);
    }

    // appends the profiles to the Maven call
    if (StringUtils.isNotBlank(getProfiles())) {
      Iterable<String> split = Splitter.on(',').split(getProfiles());
      List<String> profiles = Lists.newArrayList();
      for (String profile : split) {
        if (StringUtils.isNotBlank(profile)) {
          profile = profile.trim();
          if (StringUtils.startsWith(profile, "-")) {
            profile = StringUtils.replaceOnce(profile, "-", "!");
          }
          profiles.add(profile);
        }
      }
      if (!profiles.isEmpty()) {
        String listedProfiles = Joiner.on(',').join(profiles);
        command.append(" -P ").append(listedProfiles);
        command.append(" -Dunleash.profiles=").append(listedProfiles);
      }
    }

    if (StringUtils.isNotBlank(getReleaseArgs())) {
      command.append(" -Dunleash.releaseArgs=\"").append(getReleaseArgs().trim()).append("\"");
    }
    command.append(" -DenableLogTimestamps=").append(isUseLogTimestamps());

    if (this.hooks != null) {
      for (HookDescriptor hookData : this.hooks) {
        if (StringUtils.isNotBlank(hookData.getName()) && StringUtils.isNotBlank(hookData.getData())) {
          command.append(" -D").append(hookData.getName()).append("=\"").append(hookData.getData()).append("\"");
          if (StringUtils.isNotBlank(hookData.getRollbackData())) {
            command.append(" -D").append(hookData.getName()).append("-rollback=\"").append(hookData.getRollbackData())
                .append("\"");
          }
        }
      }
    }

    String tagNamePattern = getTagNamePattern();
    String scmMessagePrefix = getScmMessagePrefix();
    String releaseVersion = null;
    if (arguments != null) {
      tagNamePattern = arguments.getTagNamePattern();
      scmMessagePrefix = arguments.getScmMessagePrefix();
      releaseVersion = arguments.getGlobalReleaseVersion();
      if (arguments.useGlobalReleaseVersion()) {
        command.append(" -Dunleash.releaseVersion=").append(releaseVersion);
        command.append(" -Dunleash.developmentVersion=").append(arguments.getGlobalDevelopmentVersion());
      } else {
        command.append(" -Dunleash.versionUpgradeStrategy=").append(getVersionUpgradeStrategy().name());
      }
      command.append(" -Dunleash.allowLocalReleaseArtifacts=").append(arguments.allowLocalReleaseArtifacts());
      command.append(" -Dunleash.commitBeforeTagging=").append(arguments.commitBeforeTagging());
      if (arguments.errorLog()) {
        command.append(" -e");
      }
      if (arguments.debugLog()) {
        command.append(" -X");
      }
    }
    if (StringUtils.isNotBlank(tagNamePattern)) {
      command.append(" -Dunleash.tagNamePattern=\"").append(tagNamePattern.trim()).append("\"");
    }
    if (StringUtils.isNotBlank(scmMessagePrefix)) {
      command.append(" -Dunleash.scmMessagePrefix=\"").append(scmMessagePrefix.trim()).append("\"");
    }

    final Map<String, String> scmEnv = updateCommandWithScmCredentials(build, command);
    replaceJobParameterReferences(build, command);

    if (releaseVersion == null) {
      MavenModuleSet project = (MavenModuleSet) build.getProject();
      String version = null;
      Optional<Model> model = MavenUtil.parseModel(project.getRootModule(), project);
      if (model.isPresent()) {
        version = MavenUtil.parseVersion(model.get()).orNull();
      }
      if (StringUtils.isBlank(version)) {
        version = project.getRootModule().getVersion();
      }
      releaseVersion = MavenVersionUtil.calculateReleaseVersion(version);
    }
    build.addAction(new UnleashArgumentInterceptorAction(command.toString()));
    build.addAction(new UnleashBadgeAction(releaseVersion));

    return new UnleashEnvironment(scmEnv);
  }

  private Map<String, String> updateCommandWithScmCredentials(@SuppressWarnings("rawtypes") AbstractBuild build,
      StringBuilder command) {
    String scmUsername = null;
    String scmPassword = null;
    String scmSshPassphrase = null;
    String scmSshPrivateKey = null;
    if (StringUtils.isNotBlank(this.credentialsId)) {
      StandardUsernameCredentials credentials = CredentialsProvider.findCredentialById(this.credentialsId,
          StandardUsernameCredentials.class, build, URIRequirementBuilder.create().build());
      if (credentials instanceof StandardUsernamePasswordCredentials c) {
	      scmUsername = c.getUsername();
        scmPassword = c.getPassword().getPlainText();
      } else if (credentials instanceof SSHUserPrivateKey c) {
	      Secret passphrase = c.getPassphrase();
        scmSshPassphrase = passphrase != null ? passphrase.getPlainText() : null;
        final List<String> privKeys = c.getPrivateKeys();
        scmSshPrivateKey = privKeys.isEmpty() ? "" : privKeys.get(0);
      }
    }

    final Map<String, String> scmEnv = Maps.newHashMap();
    if (scmUsername != null) {
      command.append(" -Dunleash.scmUsernameEnvVar=" + ENV_VAR_SCM_USERNAME);
      scmEnv.put(ENV_VAR_SCM_USERNAME, scmUsername);
    }
    if (scmPassword != null) {
      command.append(" -Dunleash.scmPasswordEnvVar=" + ENV_VAR_SCM_PASSWORD);
      scmEnv.put(ENV_VAR_SCM_PASSWORD, scmPassword);
    }
    if (scmSshPassphrase != null) {
      command.append(" -Dunleash.scmSshPassphraseEnvVar=" + ENV_VAR_SCM_SSH_PASSPHRASE);
      scmEnv.put(ENV_VAR_SCM_SSH_PASSPHRASE, scmSshPassphrase);
    }
    if (scmSshPrivateKey != null) {
      command.append(" -Dunleash.scmSshPrivateKeyEnvVar=" + ENV_VAR_SCM_SSH_PRIVATE_KEY);
      scmEnv.put(ENV_VAR_SCM_SSH_PRIVATE_KEY, scmSshPrivateKey);
    }
    return scmEnv;
  }

  private void replaceJobParameterReferences(@SuppressWarnings("rawtypes") AbstractBuild build, StringBuilder command) {
    if (command != null) {
      ParametersAction action = build.getAction(ParametersAction.class);
      if (action != null) {
        int start = command.indexOf("${");
        while (start >= 0) {
          int end = command.indexOf("}", start);
          String name = command.substring(start + 2, end);
          ParameterValue paramValue = action.getParameter(name);
          if (paramValue != null) {
            Object value = paramValue.getValue();
            if (value != null) {
              command.replace(start, end + 1, value.toString());
            }
          }
          start = command.indexOf("${", end);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private boolean isReleaseBuild(@SuppressWarnings("rawtypes") AbstractBuild build) {
    return build.getCause(UnleashCause.class) != null;
  }

  public String getGoals() {
    return StringUtils.isBlank(this.goals) ? DescriptorImpl.DEFAULT_GOALS : this.goals;
  }

  public void setGoals(String goals) {
    this.goals = goals;
  }

  public String getProfiles() {
    return this.profiles;
  }

  public void setProfiles(String profiles) {
    this.profiles = profiles;
  }

  public String getReleaseArgs() {
    return this.releaseArgs;
  }

  public void setReleaseArgs(String releaseArgs) {
    this.releaseArgs = releaseArgs;
  }

  public boolean isUseLogTimestamps() {
    return this.useLogTimestamps;
  }

  public void setUseLogTimestamps(boolean useLogTimestamps) {
    this.useLogTimestamps = useLogTimestamps;
  }

  public String getTagNamePattern() {
    return StringUtils.isBlank(this.tagNamePattern) ? DescriptorImpl.DEFAULT_TAG_NAME_PATTERN : this.tagNamePattern;
  }

  public void setTagNamePattern(String tagNamePattern) {
    this.tagNamePattern = tagNamePattern;
  }

  public String getScmMessagePrefix() {
    return this.scmMessagePrefix;
  }

  public void setScmMessagePrefix(String scmMessagePrefix) {
    this.scmMessagePrefix = scmMessagePrefix;
  }

  public boolean isPreselectUseGlobalVersion() {
    return this.preselectUseGlobalVersion;
  }

  public void setPreselectUseGlobalVersion(boolean preselectUseGlobalVersion) {
    this.preselectUseGlobalVersion = preselectUseGlobalVersion;
  }

  @Override
  public Collection<? extends Action> getProjectActions(AbstractProject job) {
    return Collections.singleton(new UnleashAction((MavenModuleSet) job, this.preselectUseGlobalVersion,
        this.preselectAllowLocalReleaseArtifacts, this.preselectCommitBeforeTagging, false, false,
        this.versionUpgradeStrategy, getTagNamePattern(), getScmMessagePrefix()));
  }

  public List<HookDescriptor> getHooks() {
    return this.hooks;
  }

  public void setHooks(List<HookDescriptor> hooks) {
    this.hooks = hooks;
  }

  public boolean isPreselectAllowLocalReleaseArtifacts() {
    return this.preselectAllowLocalReleaseArtifacts;
  }

  public void setPreselectAllowLocalReleaseArtifacts(boolean preselectAllowLocalReleaseArtifacts) {
    this.preselectAllowLocalReleaseArtifacts = preselectAllowLocalReleaseArtifacts;
  }

  public boolean isPreselectCommitBeforeTagging() {
    return this.preselectCommitBeforeTagging;
  }

  public void setPreselectCommitBeforeTagging(boolean preselectCommitBeforeTagging) {
    this.preselectCommitBeforeTagging = preselectCommitBeforeTagging;
  }

  public String getWorkflowPath() {
    return this.workflowPath;
  }

  public void setWorkflowPath(String workflowPath) {
    this.workflowPath = workflowPath;
  }

  public String getCredentialsId() {
    return this.credentialsId;
  }

  public void setCredentialsId(String credentialsId) {
    this.credentialsId = credentialsId;
  }

  public int getNumberOfBuildsToLock() {
    return this.numberOfBuildsToLock;
  }

  public void setNumberOfBuildsToLock(int numberOfBuildsToLock) {
    this.numberOfBuildsToLock = numberOfBuildsToLock;
  }

  public VersionUpgradeStrategy getVersionUpgradeStrategy() {
    return this.versionUpgradeStrategy != null ? this.versionUpgradeStrategy : VersionUpgradeStrategy.DEFAULT;
  }

  public void setVersionUpgradeStrategy(VersionUpgradeStrategy versionUpgradeStrategy) {
    this.versionUpgradeStrategy = MoreObjects.firstNonNull(versionUpgradeStrategy, VersionUpgradeStrategy.DEFAULT);
  }

  private class UnleashEnvironment extends Environment {
    private Map<String, String> scmEnv;

    public UnleashEnvironment(Map<String, String> scmEnv) {
      this.scmEnv = scmEnv;
    }

    @Override
    public void buildEnvVars(Map<String, String> env) {
      env.putAll(this.scmEnv);
    }

    @Override
    public boolean tearDown(AbstractBuild build, BuildListener listener)
        throws IOException, InterruptedException {
      int lockedBuilds = 0;
      Result result = build.getResult();
      if (result != null && result.isBetterOrEqualTo(Result.UNSTABLE)) {
        if (UnleashMavenBuildWrapper.this.numberOfBuildsToLock != 0) {
          build.keepLog();
          lockedBuilds++;
        }
        for (@SuppressWarnings("rawtypes")
        Run run : (RunList<? extends Run>) build.getProject().getBuilds()) {
          if (isSuccessfulReleaseBuild(run)) {
            if (UnleashMavenBuildWrapper.this.numberOfBuildsToLock < 0
                || lockedBuilds < UnleashMavenBuildWrapper.this.numberOfBuildsToLock) {
              run.keepLog();
              lockedBuilds++;
            } else {
              run.keepLog(false);
            }
          }
        }
      }

      return super.tearDown(build, listener);
    }

    private boolean isSuccessfulReleaseBuild(@SuppressWarnings("rawtypes") Run run) {
      UnleashBadgeAction badgeAction = run.getAction(UnleashBadgeAction.class);
      if (badgeAction != null && !run.isBuilding()) {
        Result result = run.getResult();
        return result != null && result.isBetterOrEqualTo(Result.UNSTABLE);
      }
      return false;
    }
  }

  @Extension
  public static class DescriptorImpl extends BuildWrapperDescriptor {
    public static final String DEFAULT_GOALS = "unleash:perform";

    public static final String DEFAULT_PROFILES = "";

    public static final String DEFAULT_RELEASE_ARGS = "";

    public static final boolean DEFAULT_USE_LOG_TIMESTAMPS = true;

    public static final String DEFAULT_TAG_NAME_PATTERN = "@{project.version}";

    public static final String DEFAULT_SCM_MESSAGE_PREFIX = "[unleash-maven-plugin]";

    public static final boolean DEFAULT_PRESELECT_USE_GLOBAL_VERSION = false;

    public static final boolean DEFAULT_PRESELECT_ALLOW_LOCAL_RELEASE_ARTIFACTS = true;

    public static final boolean DEFAULT_PRESELECT_COMMIT_BEFORE_TAGGING = false;

    public static final String DEFAULT_WORKFLOW_PATH = "";

    public static final int DEFAULT_NUMBER_OF_LOCKED_BUILDS = 1;

    public static final VersionUpgradeStrategy DEFAULT_VERSION_UPGRADE_STRATEGY = VersionUpgradeStrategy.DEFAULT;

    private static final CredentialsMatcher CREDENTIALS_MATCHER = CredentialsMatchers.anyOf(
        CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
        CredentialsMatchers.instanceOf(SSHUserPrivateKey.class));

    private boolean useLogTimestamps = DEFAULT_USE_LOG_TIMESTAMPS;

    private boolean preselectAllowLocalReleaseArtifacts = DEFAULT_PRESELECT_ALLOW_LOCAL_RELEASE_ARTIFACTS;

    private boolean preselectCommitBeforeTagging = DEFAULT_PRESELECT_COMMIT_BEFORE_TAGGING;

    private String tagNamePattern = DEFAULT_TAG_NAME_PATTERN;

    private String scmMessagePrefix = DEFAULT_SCM_MESSAGE_PREFIX;

    public void setUseLogTimestamps(boolean useLogTimestamps) {
      this.useLogTimestamps = useLogTimestamps;
    }

    public boolean isUseLogTimestamps() {
      return this.useLogTimestamps;
    }

    public void setPreselectAllowLocalReleaseArtifacts(boolean preselectAllowLocalReleaseArtifacts) {
      this.preselectAllowLocalReleaseArtifacts = preselectAllowLocalReleaseArtifacts;
    }

    public boolean isPreselectAllowLocalReleaseArtifacts() {
      return this.preselectAllowLocalReleaseArtifacts;
    }

    public void setPreselectCommitBeforeTagging(boolean preselectCommitBeforeTagging) {
      this.preselectCommitBeforeTagging = preselectCommitBeforeTagging;
    }

    public boolean isPreselectCommitBeforeTagging() {
      return this.preselectCommitBeforeTagging;
    }

    public void setTagNamePattern(String tagNamePattern) {
      this.tagNamePattern = tagNamePattern;
    }

    public String getTagNamePattern() {
      return this.tagNamePattern;
    }

    public void setScmMessagePrefix(String scmMessagePrefix) {
      this.scmMessagePrefix = scmMessagePrefix;
    }

    public String getScmMessagePrefix() {
      return this.scmMessagePrefix;
    }

    @Override
    public boolean isApplicable(AbstractProject<?, ?> item) {
      return item instanceof AbstractMavenProject;
    }

    @NonNull
    @Override
    public String getDisplayName() {
      return "Unleash";
    }

    @Override
    public boolean configure(StaplerRequest2 req, JSONObject json) throws hudson.model.Descriptor.FormException {
      req.bindJSON(this, json);
      save();
      return super.configure(req, json);
    }

    // TODO adapt to be able to use SSH credentials also!
    @POST
    public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String credentialsId) {
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new ListBoxModel();
      }
      return new StandardListBoxModel().includeEmptyValue().includeMatchingAs(
          context instanceof hudson.model.Queue.Task task ? Tasks.getAuthenticationOf2(task) : ACL.SYSTEM2,
          context, StandardUsernameCredentials.class, URIRequirementBuilder.create().build(), CREDENTIALS_MATCHER);
    }

    @POST
    // Suppress issue raised by Jenkins security-scan:
    // lgtm[jenkins/no-permission-check]
    public ListBoxModel doFillVersionUpgradeStrategyItems() {
      ListBoxModel items = new ListBoxModel();
      for (VersionUpgradeStrategy strategy : VersionUpgradeStrategy.values()) {
        items.add(strategy.name(), strategy.name());
      }
      return items;
    }
  }
}
