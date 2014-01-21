/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.infrastructure.deployment;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.mule.MuleCoreExtension;
import org.mule.api.DefaultMuleException;
import org.mule.api.MuleException;
import org.mule.api.config.MuleProperties;
import org.mule.module.launcher.DeploymentListener;
import org.mule.module.launcher.DeploymentService;
import org.mule.module.launcher.MuleDeploymentService;
import org.mule.module.launcher.MulePluginClassLoaderManager;
import org.mule.module.launcher.application.Application;
import org.mule.module.launcher.coreextension.DefaultMuleCoreExtensionManager;
import org.mule.module.launcher.coreextension.MuleCoreExtensionDiscoverer;
import org.mule.module.launcher.coreextension.ReflectionMuleCoreExtensionDependencyResolver;
import org.mule.module.launcher.log4j.ArtifactAwareRepositorySelector;
import org.mule.tck.probe.PollingProber;
import org.mule.tck.probe.Probe;
import org.mule.tck.probe.Prober;
import org.mule.util.FileUtils;
import org.mule.util.FilenameUtils;
import org.mule.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.LogManager;

public class FakeMuleServer
{

    protected static final int DEPLOYMENT_TIMEOUT = 20000;

    private File muleHome;
    private File appsDir;
    private File logsDir;
    private File pluginsDir;

    private final DeploymentService deploymentService;
    private final DeploymentListener deploymentListener;

    private final List<MuleCoreExtension> coreExtensions;

    public static final String FAKE_SERVER_DISABLE_LOG_REPOSITORY_SELECTOR = "fake.server.disablelogrepositoryselector";

    static
    {
        // NOTE: this causes mule.simpleLog to no work on these tests
        if (!Boolean.getBoolean(FAKE_SERVER_DISABLE_LOG_REPOSITORY_SELECTOR))
        {
            LogManager.setRepositorySelector(new ArtifactAwareRepositorySelector(), new Object());
        }
    }

    private DefaultMuleCoreExtensionManager coreExtensionManager;

    public FakeMuleServer(String muleHomePath)
    {
        this(muleHomePath, new LinkedList<MuleCoreExtension>());
    }

    public FakeMuleServer(String muleHomePath, List<MuleCoreExtension> intialCoreExtensions)
    {
        this.coreExtensions = intialCoreExtensions;

        muleHome = new File(muleHomePath);
        muleHome.deleteOnExit();
        try
        {
            System.setProperty(MuleProperties.MULE_HOME_DIRECTORY_PROPERTY, getMuleHome().getCanonicalPath());
            System.out.println("MULE_HOME: " + getMuleHome().getCanonicalPath());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        try
        {
            setMuleFolders();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        deploymentService = new MuleDeploymentService(new MulePluginClassLoaderManager());
        deploymentListener = mock(DeploymentListener.class);
        deploymentService.addDeploymentListener(deploymentListener);

        coreExtensionManager = new DefaultMuleCoreExtensionManager(
                new MuleCoreExtensionDiscoverer()
                {
                    @Override
                    public List<MuleCoreExtension> discover() throws DefaultMuleException
                    {
                        return coreExtensions;
                    }
                },
                new ReflectionMuleCoreExtensionDependencyResolver());
        coreExtensionManager.setDeploymentService(deploymentService);
    }

    public void stop() throws MuleException
    {
        deploymentService.stop();

        coreExtensionManager.stop();
        coreExtensionManager.dispose();
    }

    public void start() throws IOException, MuleException
    {
        coreExtensionManager.initialise();
        coreExtensionManager.start();

        deploymentService.start();
    }

    public void assertDeploymentSuccess(String appName)
    {
        assertDeploymentSuccess(deploymentListener, appName);
    }

    public void assertDeploymentFailure(String appName)
    {
        assertDeploymentFailure(deploymentListener, appName);
    }

    public void assertUndeploymentSuccess(String appName)
    {
        assertUndeploymentSuccess(deploymentListener, appName);
    }

    private void assertDeploymentFailure(final DeploymentListener listener, final String appName)
    {
        Prober prober = new PollingProber(DEPLOYMENT_TIMEOUT, 100);
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                try
                {
                    verify(listener, times(1)).onDeploymentFailure(eq(appName), any(Throwable.class));
                    return true;
                }
                catch (AssertionError e)
                {
                    return false;
                }
            }

            public String describeFailure()
            {
                return "Failed to deploy application: " + appName;
            }
        });
    }

    private void assertDeploymentSuccess(final DeploymentListener listener, final String appName)
    {
        Prober prober = new PollingProber(DEPLOYMENT_TIMEOUT, 100);
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                try
                {
                    verify(listener, times(1)).onDeploymentSuccess(appName);
                    return true;
                }
                catch (AssertionError e)
                {
                    return false;
                }
            }

            public String describeFailure()
            {
                return "Failed to deploy application: " + appName;
            }
        });
    }

    public void assertUndeploymentSuccess(final DeploymentListener listener, final String appName)
    {
        Prober prober = new PollingProber(DEPLOYMENT_TIMEOUT, 100);
        prober.check(new Probe()
        {
            public boolean isSatisfied()
            {
                try
                {
                    verify(listener, times(1)).onUndeploymentSuccess(appName);
                    return true;
                }
                catch (AssertionError e)
                {
                    return false;
                }
            }

            public String describeFailure()
            {
                return "Failed to deploy application: " + appName;
            }
        });
    }

    private void setMuleFolders() throws IOException
    {
        appsDir = createFolder("apps");
        logsDir = createFolder("logs");
        pluginsDir = createFolder("plugins");
        createFolder("domains");

        File confDir = createFolder("conf");
        URL log4jFile = getClass().getResource("/log4j.properties");
        FileUtils.copyURLToFile(log4jFile, new File(confDir, "log4j.properties"));

        createFolder("lib/shared/default");
    }

    private File createFolder(String folderName)
    {
        File folder = new File(getMuleHome(), folderName);

        if (!folder.exists())
        {
            if (!folder.mkdirs())
            {
                throw new IllegalStateException(String.format("Unable to create folder '%s'", folderName));
            }
        }

        return folder;
    }

    /**
     * Copies a given app archive to the apps folder for deployment.
     */
    public void addAppArchive(URL url) throws IOException
    {
        addAppArchive(url, null);
    }

    public void deploy(String resource) throws IOException
    {
        int lastSeparator = resource.lastIndexOf(File.separator);
        String appName = StringUtils.removeEndIgnoreCase(resource.substring(lastSeparator + 1), ".zip");
        deploy(resource, appName);
    }

    public void deploy(String resource, String targetAppName) throws IOException
    {
        URL url = getClass().getResource(resource);
        addAppArchive(url, targetAppName + ".zip");
        assertDeploymentSuccess(targetAppName);
    }

    /**
     * Copies a given app archive with a given target name to the apps folder for deployment
     */
    private void addAppArchive(URL url, String targetFile) throws IOException
    {
        // copy is not atomic, copy to a temp file and rename instead (rename is atomic)
        final String tempFileName = new File((targetFile == null ? url.getFile() : targetFile) + ".part").getName();
        final File tempFile = new File(appsDir, tempFileName);
        FileUtils.copyURLToFile(url, tempFile);
        boolean renamed = tempFile.renameTo(new File(StringUtils.removeEnd(tempFile.getAbsolutePath(), ".part")));
        if (!renamed)
        {
            throw new IllegalStateException("Unable to add application archive");
        }
    }

    public void addZippedPlugin(String resource) throws IOException, URISyntaxException
    {
        URL url = getClass().getClassLoader().getResource(resource).toURI().toURL();
        String baseName = FilenameUtils.getName(url.getPath());
        File tempFile = new File(getPluginsDir(), baseName);
        FileUtils.copyURLToFile(url, tempFile);
    }

    public File getMuleHome()
    {
        return muleHome;
    }

    public File getLogsDir()
    {
        return logsDir;
    }

    public File getAppsDir()
    {
        return appsDir;
    }

    public File getPluginsDir()
    {
        return pluginsDir;
    }

    public void resetDeploymentListener()
    {
        reset(deploymentListener);
    }

    public void addCoreExtension(MuleCoreExtension coreExtension)
    {
        coreExtensions.add(coreExtension);
    }

    public void addDeploymentListener(DeploymentListener listener)
    {
        deploymentService.addDeploymentListener(listener);
    }

    public void removeDeploymentListener(DeploymentListener listener)
    {
        deploymentService.removeDeploymentListener(listener);
    }

    /**
     * Finds deployed application by name.
     *
     * @return the application if found, null otherwise
     */
    public Application findApplication(String appName)
    {
        return deploymentService.findApplication(appName);
    }
}
