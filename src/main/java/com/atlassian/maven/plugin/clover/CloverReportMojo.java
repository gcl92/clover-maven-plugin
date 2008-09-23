package com.atlassian.maven.plugin.clover;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.atlassian.maven.plugin.clover.internal.AbstractCloverMojo;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
//import org.apache.tools.ant.MagicNames;
import org.codehaus.plexus.resource.ResourceManager;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;


/**
 * Generate a <a href="http://atlassian.com/software/clover">Clover</a> report from existing Clover databases. The generated report
 * is an external report generated by Clover itself. If the project generating the report is a top level project and
 * if the <code>aggregate</code> configuration element is set to true then an aggregated report will also be created.
 * <p/>
 * <p>Note: This report mojo should be an @aggregator and the <code>clover:aggregate</code> mojo shouldn't exist. This
 * is a limitation of the site plugin which doesn't support @aggregator reports...</p>
 *
 * @version $Id: CloverReportMojo.java 555822 2007-07-13 00:03:28Z vsiveton $
 * @goal clover
 */
public class CloverReportMojo extends AbstractMavenReport {
    // TODO: Need some way to share config elements and code between report mojos and main build mojos.
    // See http://jira.codehaus.org/browse/MNG-1886

    /**
     * Use a custom report descriptor for generating your Clover Reports.
     * The format for the configuration file is identical to an Ant build file which uses the &lt;clover-report/&gt;
     * task. For a complete reference, please consult the
     * <a href="http://confluence.atlassian.com/x/34dEB">clover-report documentation</a>
     *
     * @parameter expression="${maven.clover.reportDescriptor}"
     */
    private File reportDescriptor;

    /**
     * If set to true, the clover-report configuration file will be resolved as a versioned artifact by looking for it
     * in your configured maven repositories - both remote and local.
     *
     * @parameter expression="${maven.clover.resolveReportDescriptor}" default-value="false"
     */
    private boolean resolveReportDescriptor;

    /**
     * The component that is used to resolve additional artifacts required.
     *
     * @component
     */
    protected ArtifactResolver artifactResolver;

    /**
     * Remote repositories used for the project.
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     */
    protected List repositories;

    /**
     * The component used for creating artifact instances.
     *
     * @component
     */
    protected ArtifactFactory artifactFactory;


    /**
     * The local repository.
     *
     * @parameter expression="${localRepository}"
     */
    protected ArtifactRepository localRepository;


    /**
     * The location of the <a href="http://confluence.atlassian.com/x/EIBOB">Clover database</a>.
     *
     * @parameter expression="${maven.clover.cloverDatabase}" default-value="${project.build.directory}/clover/clover.db"
     * @required
     */
    private String cloverDatabase;

    /**
     * The location of the merged clover database to create when running a report in a multimodule build.
     *
     * @parameter expression="${maven.clover.cloverMergeDatabase}" default-value="${project.build.directory}/clover/cloverMerge.db"
     * @required
     */
    private String cloverMergeDatabase;

    /**
     * The directory where the Clover report will be generated.
     *
     * @parameter expression="${maven.clover.outputDirectory}" default-value="${project.reporting.outputDirectory}/clover"
     * @required
     */
    private File outputDirectory;

    /**
     * The location where historical Clover data will be saved.
     * <p/>
     * <p>Note: It's recommended to modify the location of this directory so that it points to a more permanent
     * location as the <code>${project.build.directory}</code> directory is erased when the project is cleaned.</p>
     *
     * @parameter expression="${maven.clover.historyDir}" default-value="${project.build.directory}/clover/history"
     * @required
     */
    private String historyDir;

    /**
     * When the Clover Flush Policy is set to "interval" or threaded this value is the minimum
     * period between flush operations (in milliseconds).
     *
     * @parameter expression="${maven.clover.flushInterval}" default-value="500"
     */
    private int flushInterval;

    /**
     * If true we'll wait 2*flushInterval to ensure coverage data is flushed to the Clover database before running
     * any query on it.
     * <p/>
     * <p>Note: The only use case where you would want to turn this off is if you're running your tests in a separate
     * JVM. In that case the coverage data will be flushed by default upon the JVM shutdown and there would be no need
     * to wait for the data to be flushed. As we can't control whether users want to fork their tests or not, we're
     * offering this parameter to them.</p>
     *
     * @parameter expression="${maven.clover.waitForFlush}" default-value="true"
     */
    private boolean waitForFlush;

    /**
     * Decide whether to generate an HTML report or not.
     *
     * @parameter default-value="true" expression="${maven.clover.generateHtml}"
     */
    private boolean generateHtml;

    /**
     * Decide whether to generate a PDF report or not.
     *
     * @parameter default-value="false" expression="${maven.clover.generatePdf}"
     */
    private boolean generatePdf;

    /**
     * Decide whether to generate a XML report or not.
     *
     * @parameter default-value="true" expression="${maven.clover.generateXml}"
     */
    private boolean generateXml;

    /**
     * Decide whether to generate a JSON report or not.
     *
     * @parameter default-value="false" expression="${maven.clover.generateJson}"
     */
    private boolean generateJson;
    /**
     * Decide whether to generate a Clover historical report or not.
     *
     * @parameter default-value="false" expression="${maven.clover.generateHistorical}"
     */
    private boolean generateHistorical;

    /**
     * How to order coverage tables.
     *
     * @parameter default-value="PcCoveredAsc" expression="${maven.clover.orderBy}"
     */
    private String orderBy;

    /**
     * Comma or space separated list of Clover somesrcexcluded (block, statement or method filers) to exclude when
     * generating coverage reports.
     *
     * @parameter expression="${maven.clover.contextFilters}" default-value=""
     */
    private String contextFilters;



    /**
     * The charset to use in the html reports.
     *
     * @parameter expression="${maven.clover.charset}" default-value="UTF-8"
     */
    private String charset;

    /**
     * <p>Note: This is passed by Maven and must not be configured by the user.</p>
     *
     * @component
     */
    private Renderer siteRenderer;

    /**
     * The Maven project instance for the executing project.
     * <p/>
     * <p>Note: This is passed by Maven and must not be configured by the user.</p>
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The projects in the reactor for aggregation report.
     * <p/>
     * <p>Note: This is passed by Maven and must not be configured by the user.</p>
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List reactorProjects;

    /**
     * @parameter expression="${maven.clover.licenseLocation}"
     * @see com.atlassian.maven.plugin.clover.internal.AbstractCloverMojo#licenseLocation
     */
    private String licenseLocation;

    /**
     * @parameter expression="${maven.clover.license}"
     * @see com.atlassian.maven.plugin.clover.internal.AbstractCloverMojo#license
     */
    private String license;

    /**
     * Resource manager used to locate any Clover license file provided by the user.
     *
     * @component
     */
    private ResourceManager resourceManager;

    /**
     * A span specifies the age of the coverage data that should be used when creating a report.
     *
     * @parameter expression="${maven.clover.span}" default-value="0s"
     */
    private String span;

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#executeReport(java.util.Locale)
     */
    public void executeReport(Locale locale) throws MavenReportException {
        if (!canGenerateReport()) {
            getLog().info("No report being generated for this module.");
        }

        // Register the Clover license
        try {
            AbstractCloverMojo.registerLicenseFile(this.project, this.resourceManager, this.licenseLocation, getLog(),
                    this.getClass().getClassLoader(), this.license);
        }
        catch (MojoExecutionException e) {
            throw new MavenReportException("Failed to locate Clover license", e);
        }

        // Ensure the output directory exists
        this.outputDirectory.mkdirs();

        if (reportDescriptor == null) {
            reportDescriptor = resolveCloverDescriptor();
        } else if (!reportDescriptor.exists()){ // try finding this as a resource
            try {
                reportDescriptor = AbstractCloverMojo.getResourceAsFile(
                        project, resourceManager, reportDescriptor.getPath(), getLog(), this.getClass().getClassLoader());
            } catch (MojoExecutionException e) {
                throw new MavenReportException("Could not resolve report descriptor: " + reportDescriptor.getPath(), e);
            }
        }

        getLog().info("Using Clover report descriptor: " + reportDescriptor.getAbsolutePath());

        File singleModuleCloverDatabase = new File(this.cloverDatabase);
        if (singleModuleCloverDatabase.exists()) {
            createAllReportTypes(this.cloverDatabase, project.getArtifactId());
        }

        File mergedCloverDatabase = new File(this.cloverMergeDatabase);
        if (mergedCloverDatabase.exists()) {
            createAllReportTypes(this.cloverMergeDatabase, project.getArtifactId() + "(Aggregated)");
        }
    }

    /**
     * Example of title prefixes: "Maven Clover", "Maven Aggregated Clover"
     */
    private void createAllReportTypes(String database, String titlePrefix) throws MavenReportException {

        final String outpath = outputDirectory.getAbsolutePath();
        if (this.generateHtml) {
            createReport(database, "html", titlePrefix, outpath, outpath, false);
        }
        if (this.generatePdf) {
            createReport(database, "pdf", titlePrefix, outpath + "/clover.pdf", outpath + "/historical.pdf", true);
        }
        if (this.generateXml) {
            createReport(database, "xml", titlePrefix, outpath + "/clover.xml", null, false);
        }
        if (this.generateJson) {
            createReport(database, "json", titlePrefix, outpath, null, false);
        }
    } 

    /**
     * Note: We use Clover's <code>clover-report</code> Ant task instead of the Clover CLI APIs because the CLI
     * APIs are limited and do not support historical reports.
     */
    private void createReport(String database, String format, String title, String output, String historyOut, boolean summary) {
        final Project antProject = new Project();
        antProject.init();
        antProject.setUserProperty("ant.file", reportDescriptor.getAbsolutePath());
        antProject.setCoreLoader(getClass().getClassLoader());
        antProject.setProperty("cloverdb", database);
        antProject.setProperty("output", output);
        antProject.setProperty("history", historyDir);
        antProject.setProperty("title", title);
        final String projectDir = project.getBasedir().getPath();
        antProject.setProperty("projectDir", projectDir);
        antProject.setProperty("testPattern", "**/src/test/java/**");
        antProject.setProperty("filter", contextFilters != null ? contextFilters : "");
        antProject.setProperty("orderBy", orderBy);
        antProject.setProperty("charset", charset);
        antProject.setProperty("type", format);
        antProject.setProperty("span", span);
        antProject.setProperty("summary", String.valueOf(summary));
        if (historyOut != null) {
            antProject.setProperty("historyout", historyOut);
        }
        AbstractCloverMojo.registerCloverAntTasks(antProject, getLog());
        ProjectHelper.configureProject(antProject, reportDescriptor);
        antProject.setBaseDir(project.getBasedir());
        String target = isHistoricalDirectoryValid(output) && (historyOut != null) ? "historical" : "current";
        antProject.executeTarget(target);
    }

    private boolean isHistoricalDirectoryValid(String outFile) {
        boolean isValid = false;

        File dir = new File(this.historyDir);
        if (dir.exists()) {
            if (dir.listFiles().length > 0) {
                isValid = true;
            } else {
                getLog().warn("No Clover historical data found in [" + this.historyDir + "], skipping Clover "
                        + "historical report generation ([" + outFile + "])");
            }
        } else {
            getLog().warn("Clover historical directory [" + this.historyDir + "] does not exist, skipping Clover "
                    + "historical report generation ([" + outFile + "])");
        }

        return isValid;
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName() {
        return "clover/index";
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription(Locale locale) {
        return getBundle(locale).getString("report.clover.description");
    }

    private static ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("clover-report", locale, CloverReportMojo.class.getClassLoader());
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getOutputDirectory()
     */
    protected String getOutputDirectory() {
        return this.outputDirectory.getAbsoluteFile().toString();
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getSiteRenderer()
     */
    protected Renderer getSiteRenderer() {
        return this.siteRenderer;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getProject()
     */
    protected MavenProject getProject() {
        return this.project;
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName(Locale locale) {
        return getBundle(locale).getString("report.clover.name");
    }

    /**
     * Always return true as we're using the report generated by Clover rather than creating our own report.
     *
     * @return true
     */
    public boolean isExternalReport() {
        return true;
    }

    /**
     * Generate reports if a Clover module database or a Clover merged database exist.
     *
     * @return true if a project should be generated
     * @see org.apache.maven.reporting.AbstractMavenReport#canGenerateReport()
     */
    public boolean canGenerateReport() {
        boolean canGenerate = false;


        AbstractCloverMojo.waitForFlush(this.waitForFlush, this.flushInterval);

        File singleModuleCloverDatabase = new File(this.cloverDatabase);
        File mergedCloverDatabase = new File(this.cloverMergeDatabase);

        if (singleModuleCloverDatabase.exists() || mergedCloverDatabase.exists()) {
            if (this.generateHtml || this.generatePdf || this.generateXml) {
                canGenerate = true;
            }
        } else {
            getLog().warn("No Clover database found, skipping report generation");
        }

        return canGenerate;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#setReportOutputDirectory(java.io.File)
     */
    public void setReportOutputDirectory(File reportOutputDirectory) {
        if ((reportOutputDirectory != null) && (!reportOutputDirectory.getAbsolutePath().endsWith("clover"))) {
            this.outputDirectory = new File(reportOutputDirectory, "clover");
        } else {
            this.outputDirectory = reportOutputDirectory;
        }
    }

    /**
     * The logic here is taken from AbstractSiteRenderingMojo#resolveSiteDescriptor in the maven-site-plugin.
     * See also: http://docs.codehaus.org/display/MAVENUSER/Mojo+Developer+Cookbook
     *
     * @return the clover report configuration file to use
     * @throws MavenReportException if at least the default file can't be resolved
     */
    protected File resolveCloverDescriptor()
            throws MavenReportException {

        if (resolveReportDescriptor) {
            getLog().info("Attempting to resolve the clover-report configuration as an xml artifact.");
            Artifact artifact = artifactFactory.createArtifactWithClassifier(
                    project.getGroupId(),
                    project.getArtifactId(),
                    project.getVersion(),
                    "xml", "clover-report");

            try {
                artifactResolver.resolve(artifact, repositories, localRepository);
                return artifact.getFile();
            } catch (ArtifactResolutionException e) {
                getLog().warn(e.getMessage(), e);
            } catch (ArtifactNotFoundException e) {
                getLog().warn(e.getMessage(), e);
            }
        }
        try {
            getLog().info("Using /default-clover-report descriptor.");
            final File file = AbstractCloverMojo.getResourceAsFile(project,
                    resourceManager,
                    "/default-clover-report.xml",
                    getLog(),
                    this.getClass().getClassLoader());
            file.deleteOnExit();
            return file;

        } catch (Exception e) {
            throw new MavenReportException("Could not resolve default-clover-report.xml. " +
                    "Please try specifying this via the maven.clover.reportDescriptor property.", e);
        }
    }

}
 