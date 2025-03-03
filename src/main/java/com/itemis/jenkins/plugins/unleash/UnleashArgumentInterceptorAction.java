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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import hudson.maven.MavenArgumentInterceptorAction;
import hudson.maven.MavenModuleSetBuild;
import hudson.util.ArgumentListBuilder;

/**
 * @author Stanley Hillner
 */
// This class was developed based on org.jvnet.hudson.plugins.m2release.M2ReleaseArgumentInterceptorAction
// The class still contains substantial parts of the original implementation
// original authors: Dominik Bartholdi & Robert Kleinschmager
public class UnleashArgumentInterceptorAction implements MavenArgumentInterceptorAction {
  private static final Logger LOGGER = Logger.getLogger(UnleashArgumentInterceptorAction.class.getName());
  private transient String goalsAndOptions;

  public UnleashArgumentInterceptorAction(String goalsAndOptions) {
    this.goalsAndOptions = goalsAndOptions;
  }

  @Override
  public String getIconFileName() {
    return null;
  }

  @Override
  public String getDisplayName() {
    return null;
  }

  @Override
  public String getUrlName() {
    return null;
  }

  @Override
  public String getGoalsAndOptions(MavenModuleSetBuild build) {
    return this.goalsAndOptions;
  }

  @Override
  public ArgumentListBuilder intercept(ArgumentListBuilder mavenargs, MavenModuleSetBuild build) {
    ArgumentListBuilder returnListBuilder;
    List<String> argumentList = mavenargs.toList();
    if (build.getProject().isIncrementalBuild() && containsJenkinsIncrementalBuildArguments(argumentList)) {
      LOGGER.config(
          "This Maven build seems to be configured as 'Incremental build'. This will be disabled, as always the full project will be released");
      returnListBuilder = removeAllIncrementalBuildArguments(mavenargs.clone());
    } else {
      returnListBuilder = mavenargs.clone();
    }

    return returnListBuilder;
  }

  private boolean containsJenkinsIncrementalBuildArguments(List<String> mavenargs) {
    int amdIndex = mavenargs.indexOf("-amd");
    int plIndex = mavenargs.indexOf("-pl");

    boolean amdArgumentExists = amdIndex != -1;
    boolean plArgumentExists = plIndex != -1;

    if (amdArgumentExists && plArgumentExists) {
      boolean amdAndPlArgumentAreInSupposedOrder = amdIndex == plIndex - 1;
      // assuming, that the argument behind -pl is the list of projects, as added in {@link MavenModuleSetBuild}

      return amdAndPlArgumentAreInSupposedOrder && thereIsAnArgumentBehinPlArgument(mavenargs, plIndex);
    } else {
      return false;

    }
  }

  private boolean thereIsAnArgumentBehinPlArgument(List<String> mavenargs, int plIndex) {
    if (mavenargs.size() >= plIndex + 1) {
      return mavenargs.get(plIndex + 1) != null;
    }
    return false;
  }

  private ArgumentListBuilder removeAllIncrementalBuildArguments(ArgumentListBuilder mavenargs) {

    // remove the three elements which was added by MavenModuleSetBuild
    // -amd
    // -pl
    // <list of modules>
    LOGGER.finer("Start removing the arguments '-amd -pl <list of modules>' from argument list");

    ArgumentListBuilder returnListBuilder = new ArgumentListBuilder();

    int amdIndex = mavenargs.toList().indexOf("-amd");

    ensureArgumentsAndMaskHaveSaveSize(mavenargs);

    boolean[] maskArray = mavenargs.toMaskArray();
    ArrayList<Boolean> maskList = Lists.newArrayList();
    for (boolean b : maskArray) {
      maskList.add(b);
    }

    List<String> oldArgumentList = mavenargs.toList();

    // as List.remove is shifting all elements, we can reuse the index
    String removedAmd = oldArgumentList.remove(amdIndex);
    Preconditions.checkArgument("-amd".equals(removedAmd));
    maskList.remove(amdIndex);

    String removedPl = oldArgumentList.remove(amdIndex);
    Preconditions.checkArgument("-pl".equals(removedPl));
    maskList.remove(amdIndex);

    String removedModuleList = oldArgumentList.remove(amdIndex);
    maskList.remove(amdIndex);
    LOGGER.finer(String.format("Removed the arguments '-amd -pl %s' from argument list", removedModuleList));

    // rebuild
    for (int i = 0; i < oldArgumentList.size(); i++) {
      returnListBuilder.add(oldArgumentList.get(i), maskList.get(i));
    }

    ensureArgumentsAndMaskHaveSaveSize(returnListBuilder);

    LOGGER.fine(String.format("Rebuild maven argument list, old size=%s; new size=%s", oldArgumentList.size(),
        returnListBuilder.toList().size()));

    return returnListBuilder;
  }

  private void ensureArgumentsAndMaskHaveSaveSize(ArgumentListBuilder alb) {
    if (alb.toList().size() != alb.toMaskArray().length) {
      throw new RuntimeException("could not intercept argument list: ArgumentList and Mask are out of sync ");
    }
  }
}
