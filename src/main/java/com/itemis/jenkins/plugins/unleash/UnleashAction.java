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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.model.Model;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.StaplerResponse2;
import org.kohsuke.stapler.verb.POST;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.itemis.jenkins.plugins.unleash.permalinks.LastFailedReleasePermalink;
import com.itemis.jenkins.plugins.unleash.permalinks.LastSuccessfulReleasePermalink;
import com.itemis.jenkins.plugins.unleash.util.MavenUtil;
import com.itemis.maven.plugins.unleash.util.MavenVersionUtil;
import com.itemis.maven.plugins.unleash.util.VersionUpgradeStrategy;

import hudson.maven.MavenModule;
import hudson.maven.MavenModuleSet;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PermalinkProjectAction;
import hudson.security.Permission;
import hudson.security.PermissionGroup;
import hudson.security.PermissionScope;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

/**
 * @author Stanley Hillner
 */
// This class was developed based on org.jvnet.hudson.plugins.m2release.M2ReleaseAction
// The class still contains substantial parts of the original implementation
// original authors: James Nord & Dominik Bartholdi
public class UnleashAction implements PermalinkProjectAction {
  @SuppressWarnings("unused")
  private static final Logger LOGGER = Logger.getLogger(UnleashAction.class.getName());

  public static final PermissionGroup PERMISSIONS = new PermissionGroup(UnleashAction.class,
      Messages._UnleashAction_PermissionsTitle());

  /**
   * Permission to trigger release builds.
   */
  public static final Permission RELEASE_PERMISSION = new Permission(PERMISSIONS, "Release",
      Messages._UnleashAction_ReleasePermission_Description(), Jenkins.ADMINISTER, PermissionScope.ITEM);

  private MavenModuleSet project;

  private boolean useGlobalVersion;

  private boolean allowLocalReleaseArtifacts;

  private boolean commitBeforeTagging;

  private boolean errorLog;

  private boolean debugLog;

  private VersionUpgradeStrategy versionUpgradeStrategy;

  private String tagNamePattern;

  private String scmMessagePrefix;

  public UnleashAction(MavenModuleSet project, boolean useGlobalVersion, boolean allowLocalReleaseArtifacts,
      boolean commitBeforeTagging, boolean errorLog, boolean debugLog, VersionUpgradeStrategy versionUpgradeStrategy,
      String tagNamePattern, String scmMessagePrefix) {
    this.project = project;
    this.useGlobalVersion = useGlobalVersion;
    this.allowLocalReleaseArtifacts = allowLocalReleaseArtifacts;
    this.commitBeforeTagging = commitBeforeTagging;
    this.errorLog = errorLog;
    this.debugLog = debugLog;
    this.versionUpgradeStrategy = versionUpgradeStrategy;
    this.tagNamePattern = tagNamePattern;
    this.scmMessagePrefix = scmMessagePrefix;
  }

  public List<ParameterDefinition> getParameterDefinitions() {
    ParametersDefinitionProperty property = this.project.getProperty(ParametersDefinitionProperty.class);
    List<ParameterDefinition> params = Collections.emptyList();
    if (property != null) {
      params = property.getParameterDefinitions();
    }
    return params;
  }

  @Override
  public String getIconFileName() {
    return "/plugin/unleash/img/unleash.png";
  }

  @Override
  public String getDisplayName() {
    return "Trigger Unleash Maven Plugin";
  }

  @Override
  public String getUrlName() {
    return "unleash";
  }

  @Override
  public List<Permalink> getPermalinks() {
    return Lists.newArrayList(LastSuccessfulReleasePermalink.INSTANCE, LastFailedReleasePermalink.INSTANCE);
  }

  public String computeReleaseVersion() {
    String version = null;
    Optional<Model> model = MavenUtil.parseModel(this.project.getRootModule(), this.project);
    if (model.isPresent()) {
      Optional<String> parsedVersion = MavenUtil.parseVersion(model.get());
      if (parsedVersion.isPresent()) {
        version = MavenVersionUtil.calculateReleaseVersion(parsedVersion.get());
      }
    }

    if (StringUtils.isBlank(version)) {
      MavenModule rootModule = this.project.getRootModule();
      if (rootModule != null && StringUtils.isNotBlank(rootModule.getVersion())) {
        version = MavenVersionUtil.calculateReleaseVersion(rootModule.getVersion());
      }
    }

    if (StringUtils.isNotBlank(version)) {
      return version;
    }
    return "NaN";
  }

  public String computeReleaseVersion(MavenModule module) {
    String version = null;
    if (module != null) {
      Optional<Model> model = MavenUtil.parseModel(module, this.project);
      if (model.isPresent()) {
        Optional<String> parsedVersion = MavenUtil.parseVersion(model.get());
        if (parsedVersion.isPresent()) {
          version = MavenVersionUtil.calculateReleaseVersion(parsedVersion.get());
        }
      }

      if (StringUtils.isBlank(version) && StringUtils.isNotBlank(module.getVersion())) {
        version = MavenVersionUtil.calculateReleaseVersion(module.getVersion());
      }
    }

    if (StringUtils.isNotBlank(version)) {
      return version;
    }
    return "NaN";
  }

  public String computeNextDevelopmentVersion() {
    String version = null;
    Optional<Model> model = MavenUtil.parseModel(this.project.getRootModule(), this.project);
    if (model.isPresent()) {
      Optional<String> parsedVersion = MavenUtil.parseVersion(model.get());
      if (parsedVersion.isPresent()) {
        version = MavenVersionUtil.calculateNextSnapshotVersion(parsedVersion.get(), this.versionUpgradeStrategy);
      }
    }

    if (StringUtils.isBlank(version)) {
      MavenModule rootModule = this.project.getRootModule();
      if (rootModule != null && StringUtils.isNotBlank(rootModule.getVersion())) {
        version = MavenVersionUtil.calculateNextSnapshotVersion(rootModule.getVersion(), this.versionUpgradeStrategy);
      }
    }

    if (StringUtils.isNotBlank(version)) {
      return version;
    }
    return "NaN";
  }

  public String computeNextDevelopmentVersion(MavenModule module) {
    String version = null;
    if (module != null) {
      Optional<Model> model = MavenUtil.parseModel(this.project.getRootModule(), this.project);
      if (model.isPresent()) {
        Optional<String> parsedVersion = MavenUtil.parseVersion(model.get());
        if (parsedVersion.isPresent()) {
          version = MavenVersionUtil.calculateNextSnapshotVersion(parsedVersion.get(), this.versionUpgradeStrategy);
        }
      }

      if (StringUtils.isBlank(version) && StringUtils.isNotBlank(module.getVersion())) {
        version = MavenVersionUtil.calculateNextSnapshotVersion(module.getVersion(), this.versionUpgradeStrategy);
      }
    }

    if (StringUtils.isNotBlank(version)) {
      return version;
    }
    return "NaN";

  }

  public boolean isUseGlobalVersion() {
    return this.useGlobalVersion;
  }

  public boolean isNotUseGlobalVersion() {
    return !this.useGlobalVersion;
  }

  public void setUseGlobalVersion(boolean useGlobalVersion) {
    this.useGlobalVersion = useGlobalVersion;
  }

  public boolean isAllowLocalReleaseArtifacts() {
    return this.allowLocalReleaseArtifacts;
  }

  public void setAllowLocalReleaseArtifacts(boolean allowLocalReleaseArtifacts) {
    this.allowLocalReleaseArtifacts = allowLocalReleaseArtifacts;
  }

  public boolean isCommitBeforeTagging() {
    return this.commitBeforeTagging;
  }

  public void setCommitBeforeTagging(boolean commitBeforeTagging) {
    this.commitBeforeTagging = commitBeforeTagging;
  }

  public boolean isErrorLog() {
    return this.errorLog;
  }

  public void setErrorLog(boolean errorLog) {
    this.errorLog = errorLog;
  }

  public boolean isDebugLog() {
    return this.debugLog;
  }

  public void setDebugLog(boolean debugLog) {
    this.debugLog = debugLog;
  }

  public String getTagNamePattern() {
    return this.tagNamePattern;
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

  public List<MavenModule> getAllMavenModules() {
    List<MavenModule> modules = Lists.newArrayList();
    modules.addAll(this.project.getModules());
    return modules;
  }

  @POST
  public void doSubmit(StaplerRequest2 req, StaplerResponse2 resp) throws IOException, ServletException {
    // verify permission
    this.project.checkPermission(RELEASE_PERMISSION);

    RequestWrapper requestWrapper = new RequestWrapper(req);

    UnleashArgumentsAction arguments = new UnleashArgumentsAction();
    boolean globalVersions = requestWrapper.getBoolean("useGlobalVersion");

    arguments.setUseGlobalReleaseVersion(globalVersions);
    if (globalVersions) {
      arguments.setGlobalReleaseVersion(requestWrapper.getString("releaseVersion"));
      arguments.setGlobalDevelopmentVersion(requestWrapper.getString("developmentVersion"));
    } else {
      arguments.setGlobalReleaseVersion(computeReleaseVersion());
      arguments.setGlobalDevelopmentVersion(computeNextDevelopmentVersion());
    }

    arguments.setAllowLocalReleaseArtifacts(requestWrapper.getBoolean("allowLocalReleaseArtifacts"));
    arguments.setCommitBeforeTagging(requestWrapper.getBoolean("commitBeforeTagging"));
    arguments.setErrorLog(requestWrapper.getBoolean("errorLog"));
    arguments.setDebugLog(requestWrapper.getBoolean("debugLog"));
    arguments.setTagNamePattern(requestWrapper.getString("tagNamePattern"));
    arguments.setScmMessagePrefix(requestWrapper.getString("scmMessagePrefix"));

    // get the normal job parameters (adapted from
    // hudson.model.ParametersDefinitionProperty._doBuild(StaplerRequest,
    // StaplerResponse))
    List<ParameterValue> params = new ArrayList<>();
    JSONObject formData = req.getSubmittedForm();
    JSONArray a = JSONArray.fromObject(formData.get("parameter"));
    for (Object o : a) {
      if (o instanceof JSONObject) {
        JSONObject jo = (JSONObject) o;
        if (!jo.isNullObject()) {
          String name = jo.optString("name");
          if (name != null) {
            ParameterDefinition d = getParameterDefinition(name);
            if (d == null) {
              throw new IllegalArgumentException("No such parameter definition: " + name);
            }
            ParameterValue parameterValue = d.createValue(req, jo);
            params.add(parameterValue);
          }
        }
      }
    }

    if (this.project.scheduleBuild(0, new UnleashCause(), new ParametersAction(params), arguments)) {
      resp.sendRedirect(req.getContextPath() + '/' + this.project.getUrl());
    } else {
      resp.sendRedirect(req.getContextPath() + '/' + this.project.getUrl() + '/' + getUrlName() + "/failed");
    }
  }

  public ParameterDefinition getParameterDefinition(String name) {
    for (ParameterDefinition pd : getParameterDefinitions()) {
      if (pd.getName().equals(name)) {
        return pd;
      }
    }
    return null;
  }

  static class RequestWrapper {
    private final StaplerRequest2 request;

    public RequestWrapper(StaplerRequest2 request) {
      this.request = request;
    }

    private String getString(String key) {
      Map<String, ?> parameters = this.request.getParameterMap();
      Object o = parameters.get(key);
      if (o != null) {
        if (o instanceof String) {
          return (String) o;
        } else if (o.getClass().isArray()) {
          Object firstParam = ((Object[]) o)[0];
          if (firstParam instanceof String) {
            return (String) firstParam;
          }
        }
      }
      return null;
    }

    private boolean getBoolean(String key) {
      Map<String, ?> parameters = this.request.getParameterMap();
      String flag = null;

      Object o = parameters.get(key);
      if (o != null) {
        if (o instanceof String) {
          flag = (String) o;
        } else if (o.getClass().isArray()) {
          Object firstParam = ((Object[]) o)[0];
          if (firstParam instanceof String) {
            flag = (String) firstParam;
          }
        }
      }
      return Objects.equal("true", flag) || Objects.equal("on", flag);
    }
  }
}
