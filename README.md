# Unleash Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/unleash.svg)](https://plugins.jenkins.io/unleash)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/unleash-plugin.svg?label=release)](https://github.com/jenkinsci/unleash-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/unleash.svg?color=blue)](https://plugins.jenkins.io/unleash)
[![GitHub license](https://img.shields.io/github/license/jenkinsci/unleash-plugin?label=license)](./LICENSE)
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins%2Funleash-plugin%2Fmaster)](https://ci.jenkins.io/job/Plugins/job/unleash-plugin/job/master/)
[![Maintenance](https://img.shields.io/maintenance/yes/2099.svg)]()

This is a Jenkins Build Wrapper for Maven Jobs. It enables you to
perform Maven releases using the
[Apache Maven Unleash Plugin](https://github.com/mavenplugins/unleash-maven-plugin).<br>
That plugin is providing significant advantage for a Maven release process versus the original Maven release plugin like:
- Checking for SNAPSHOT dependencies
- Rolling back previous step transactions - like SCM commits - in case of a release process step failing
- CDI based custom extendable default workflows

See unleash-maven-plugin [Wiki](https://github.com/mavenplugins/unleash-maven-plugin/wiki) for details.

# 🚀 Dev Continued - Java 8, 11, 17, 21 Ready 🚀 

This plugin has **recovered** from deprecation tagged by 21-Dec-2022 since
the [Apache Maven Unleash Plugin](https://github.com/mavenplugins/unleash-maven-plugin) did continue to be actively maintained.
That plugin did move by a new `groupId` to Maven coordinate `io.github.mavenplugins:unleash-maven-plugin`.<br>
It is working seamlessly for Maven >= 3.3.9 on Java 8, 11, 17 and 21 since unleash-maven-plugin version 3.0.0.

# How To Use The Plugin?

This section describes the project configuration options as well as the
actual usage of the plugin when building Maven releases.

### Project Configuration

In order to use the plugin for building Maven releases you will have to
enable it first in the configuration of your Maven build job. Go to the
**Build Environment** section and enable the build wrapper by checking
the **Unleash** box.  
Then you will see some input fields for basic settings. There you can
add the required profiles and options for your release build. The goals
are pre-defined because currently unleash:perform is the only goals that
makes sense there.  
![](docs/images/2016-08-25_14h50_44.png)

Clicking the Advanced... button shows some additional settings for
workflow overriding, SCM-related settings and some checkboxes that
affect the actual release form that we use later.  
![](docs/images/2016-08-25_14h51_17.png)

The Hook Data section can then be used for really advanced stuff. If you
f.i. extend the default workflow and add an exec-hook to the workflow,
you will be able to provide data for the hook execution at this point.  
![](docs/images/2016-08-25_14h52_08.png)

### Use The Plugin

The usage of the plugin is pretty simple and is nothing more than
submitting a form on a subpage of the project.  
After you've enabled the plugin a link will be shown on the left side of
the screen called **Trigger Unleash Maven Plugin**:  
![](docs/images/Screenshot.png)

After clicking on this link you will be directed to the form page where
you can start the release build for your project.  
![](docs/images/2016-08-25_14h53_56.png)

On this page you can either specify a global version for all modules of
the project (for release and dev) or you stick to the versions provided
by the plugin and listed in the table which allows idependent versioning
of modules.  
You can also provide custom SCM credentials and reqeuest some other
stuff.  
![](docs/images/2016-08-25_14h54_06.png)

### Release Info

Once you have some successful or failed release builds you can also get
some project-related release information, such as the badge icons
indicating successful or failed releases.  
![](docs/images/2016-07-27_14h05_10.png)

There are also two permalinks available, one for successful and one for
failed releases.  
![](docs/images/Screenshot-1.png)

Finally there are some ListView columns:  
![](docs/images/Screenshot-5.png)

# Support

Issues around this plugin are tracked in the Jenkins Issue Tracker. To
browse open issues please visit [this
page](https://issues.jenkins-ci.org/secure/IssueNavigator.jspa?mode=hide&reset=true&jqlQuery=project+%3D+JENKINS+AND+status+in+(Open,+%22In+Progress%22,+Reopened)+AND+component+%3D+unleash-plugin).
There you can also file new ones if necessary.

General information about the usage of the Maven plugin can be found
here:
[unleash-maven-plugin](https://github.com/mavenplugins/unleash-maven-plugin).

# Copyright Information

Substantial parts of the plugin sources have been extracted from the M2
Release Plugin. It served as a base for getting started since the
feature set was very similar.  
Please find further copyright notes in the source code.

# Version History

See [Releases](https://github.com/jenkinsci/unleash-plugin/releases)