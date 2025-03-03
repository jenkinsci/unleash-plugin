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
package com.itemis.jenkins.plugins.unleash.views;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.kohsuke.stapler.DataBoundConstructor;

import com.itemis.jenkins.plugins.unleash.UnleashBadgeAction;
import com.itemis.jenkins.plugins.unleash.permalinks.LastSuccessfulReleasePermalink;

import hudson.Extension;
import hudson.maven.AbstractMavenProject;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;

/**
 * @author Stanley Hillner
 */
// This class was developed based on org.jvnet.hudson.plugins.m2release.LastReleaseListViewColumn
// The class still contains substantial parts of the original implementation
// original authors: Kohsuke Kawaguchi
public class LastSuccessfulReleaseListViewColumn extends ListViewColumn {
  @DataBoundConstructor
  public LastSuccessfulReleaseListViewColumn() {
  }

  public Info getLastReleaseInfoOf(AbstractMavenProject<?, ?> project) {
    Run<?, ?> run = LastSuccessfulReleasePermalink.INSTANCE.resolve(project);
    if (run != null) {
      return new Info((AbstractBuild<?, ?>) run);
    }
    return null;
  }

  public static class Info {
    public final AbstractBuild<?, ?> build;
    public final UnleashBadgeAction action;

    Info(AbstractBuild<?, ?> build) {
      this.build = build;
      this.action = build.getAction(UnleashBadgeAction.class);
      assert this.action != null;
    }
  }

  @Extension
  public static class DescriptorImpl extends ListViewColumnDescriptor {
    @NonNull
    @Override
    public String getDisplayName() {
      return "Last Successful Release (unleash-maven-plugin)";
    }

    @Override
    public boolean shownByDefault() {
      return false;
    }
  }
}
