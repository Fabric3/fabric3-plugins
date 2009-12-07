/*
 * Fabric3
 * Copyright (c) 2009 Metaform Systems
 *
 * Fabric3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version, with the
 * following exception:
 *
 * Linking this software statically or dynamically with other
 * modules is making a combined work based on this software.
 * Thus, the terms and conditions of the GNU General Public
 * License cover the whole combination.
 *
 * As a special exception, the copyright holders of this software
 * give you permission to link this software with independent
 * modules to produce an executable, regardless of the license
 * terms of these independent modules, and to copy and distribute
 * the resulting executable under terms of your choice, provided
 * that you also meet, for each linked independent module, the
 * terms and conditions of the license of that module. An
 * independent module is a module which is not derived from or
 * based on this software. If you modify this software, you may
 * extend this exception to your version of the software, but
 * you are not obligated to do so. If you do not wish to do so,
 * delete this exception statement from your version.
 *
 * Fabric3 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the
 * GNU General Public License along with Fabric3.
 * If not, see <http://www.gnu.org/licenses/>.
 *
 * ----------------------------------------------------
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 *
 */
package org.fabric3.war;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.metadata.ResolutionGroup;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;

/**
 * Add Fabric3 runtime dependencies to a webapp. The webapp runtime loads user and runtime libraries using the webapp classloader. Consequently, all
 * runtime, extension, and user jars are added to the WEB-INF/lib directory. The list of system extensions are specified in the properties file
 * f3Extensions.properties.
 * <p/>
 * Extensions are exploded and the contents of the META-INF/lib directory are copied to the WEB-INF/lib directory.
 * <p/>
 * <p/>
 * Performs the following tasks.
 * <p/>
 * <ul> <li>Adds the boot dependencies transitively to WEB-INF/lib</li> <li>By default boot libraries are transitively resolved from webapp-host</li>
 * <li>The version of boot libraries can be specified using configuration/runTimeVersion element</li> <li>Boot libraries can be overridden using the
 * configuration/bootLibs element in the plugin</li> <li>Adds the extension artifacts specified using configuration/extensions to WEB-INF/lib</li>
 * </ul>
 *
 * @version $Rev$ $Date$
 * @goal fabric3-war
 * @phase generate-resources
 */
public class Fabric3WarMojo extends AbstractMojo {

    /**
     * Fabric3 boot path.
     */
    private static final String BOOT_PATH = "WEB-INF/lib";

    /**
     * The directory where the webapp is built.
     *
     * @parameter expression="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    public File webappDirectory;

    /**
     * Artifact metadata source.
     *
     * @component
     */
    public ArtifactMetadataSource metadataSource;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    public ArtifactFactory artifactFactory;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     * @parameter expression="${component.org.apache.maven.artifact.resolver.ArtifactResolver}"
     * @required
     * @readonly
     */
    public ArtifactResolver resolver;

    /**
     * Location of the local repository.
     *
     * @parameter expression="${localRepository}"
     * @readonly
     * @required
     */
    public ArtifactRepository localRepository;

    /**
     * List of Remote Repositories used by the resolver
     *
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @readonly
     * @required
     */
    public List remoteRepositories;

    /**
     * The directory for the generated WAR.
     *
     * @parameter
     */
    public Dependency[] bootLibs;

    /**
     * Set of extension artifacts that should be deployed to the runtime.
     *
     * @parameter
     */
    public Dependency[] extensions;

    /**
     * The default version of the runtime to use.
     *
     * @parameter expression="RELEASE"
     */
    public String runtimeVersion;

    /**
     * Exclude any embedded dependencies from extensions.
     *
     * @parameter
     */
    public List<String> excludes = new LinkedList<String>();

    /**
     * POM
     *
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    public MavenProject project;

    public Fabric3WarMojo() throws ParserConfigurationException {
    }

    /**
     * Executes the MOJO.
     */
    public void execute() throws MojoExecutionException {
        try {
            installRuntime();
            installExtensions();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void installRuntime() throws MojoExecutionException, IOException {

        getLog().info("Using fabric3 runtime version " + runtimeVersion);

        if (bootLibs == null) {
            Dependency dependancy = new Dependency("org.codehaus.fabric3.webapp", "fabric3-webapp-host", runtimeVersion);
            bootLibs = new Dependency[]{dependancy};
        }

        File bootDir = new File(webappDirectory, BOOT_PATH);
        bootDir.mkdirs();
        for (Dependency dependency : bootLibs) {
            if (dependency.getVersion() == null) {
                resolveDependencyVersion(dependency);
            }
            for (Artifact artifact : resolveArtifact(dependency.getArtifact(artifactFactory), true)) {
                FileUtils.copyFileToDirectoryIfModified(artifact.getFile(), bootDir);
            }
        }
    }

    private void installExtensions() throws MojoExecutionException {

        Set<Dependency> uniqueExtensions = new HashSet<Dependency>();
        if (extensions != null) {
            for (Dependency extension : extensions) {
                if (extension.getVersion() == null) {
                    resolveDependencyVersion(extension);
                }
                uniqueExtensions.add(extension);
            }
        }

        uniqueExtensions.addAll(getCoreExtensions());


        processExtensions(BOOT_PATH, "f3Extensions.properties", uniqueExtensions);


    }

    private void processExtensions(String extenstionsPath, String extensionProperties, Set<Dependency> extensions) throws MojoExecutionException {

        try {
            Properties props = new Properties();
            File extensionsDir = new File(webappDirectory, extenstionsPath);

            // process Maven dependencies
            for (Dependency dependency : extensions) {

                if (dependency.getVersion() == null) {
                    resolveDependencyVersion(dependency);
                }

                Artifact extensionArtifact = dependency.getArtifact(artifactFactory);
                extensionArtifact = resolveArtifact(extensionArtifact, false).iterator().next();

                File deflatedExtensionFile = new File(extensionsDir, extensionArtifact.getFile().getName());
                JarOutputStream deflatedExtensionOutputStream = new JarOutputStream(new FileOutputStream(deflatedExtensionFile));

                JarFile extensionFile = new JarFile(extensionArtifact.getFile());
                Enumeration<JarEntry> entries = extensionFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry jarEntry = entries.nextElement();
                    String entryName = jarEntry.getName();
                    if (entryName.startsWith("META-INF/lib") && entryName.endsWith(".jar")) {
                        String extractedLibraryName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        if (excludes.contains(extractedLibraryName)) {
                            continue;
                        }
                        File extractedLibraryFile = new File(extensionsDir, extractedLibraryName);
                        if (!extractedLibraryFile.exists()) {
                            FileOutputStream outputStream = new FileOutputStream(extractedLibraryFile);
                            InputStream inputStream = extensionFile.getInputStream(jarEntry);
                            IOUtil.copy(inputStream, outputStream);
                            IOUtil.close(inputStream);
                            IOUtil.close(outputStream);
                        }
                    } else {
                        deflatedExtensionOutputStream.putNextEntry(jarEntry);
                        InputStream inputStream = extensionFile.getInputStream(jarEntry);
                        IOUtil.copy(inputStream, deflatedExtensionOutputStream);
                        IOUtil.close(inputStream);
                    }
                }

                IOUtil.close(deflatedExtensionOutputStream);

                props.put(extensionArtifact.getFile().getName(), extensionArtifact.getFile().getName());

            }

            props.store(new FileOutputStream(new File(extensionsDir, extensionProperties)), null);

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    /**
     * Resolve the dependency for the given extension from the dependencyManagement from the pom
     *
     * @param extension the dependcy information for the extension
     */
    @SuppressWarnings({"unchecked"})
    private void resolveDependencyVersion(Dependency extension) {
        List<org.apache.maven.model.Dependency> dependencies = project.getDependencyManagement().getDependencies();
        for (org.apache.maven.model.Dependency dependecy : dependencies) {
            if (!dependecy.getGroupId().startsWith("org.fabric3")){
                // hack: do not set version of non-F3 dependencies
                continue;
            }
            if (dependecy.getGroupId().equals(extension.getGroupId()) && dependecy.getArtifactId().equals(extension.getArtifactId())) {
                extension.setVersion(dependecy.getVersion());

            }
        }
    }

    /**
     * Resolves the specified artifact.
     *
     * @param artifact   Artifact to be resolved.
     * @param transitive Whether to resolve transitively.
     * @return A set of resolved artifacts.
     * @throws MojoExecutionException if there is an error resolving the artifact
     */
    private Set<Artifact> resolveArtifact(Artifact artifact, boolean transitive) throws MojoExecutionException {

        try {

            Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();

            // Resolve the artifact
            resolver.resolve(artifact, remoteRepositories, localRepository);
            resolvedArtifacts.add(artifact);

            if (!transitive) {
                return resolvedArtifacts;
            }

            // Transitively resolve all the dependencies
            ResolutionGroup resolutionGroup = metadataSource.retrieve(artifact, localRepository, remoteRepositories);
            ArtifactResolutionResult result = resolver.resolveTransitively(resolutionGroup.getArtifacts(),
                                                                           artifact,
                                                                           remoteRepositories,
                                                                           localRepository,
                                                                           metadataSource);

            // Add the artifacts to the deployment unit
            for (Object depArtifact : result.getArtifacts()) {
                resolvedArtifacts.add((Artifact) depArtifact);
            }
            return resolvedArtifacts;

        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } catch (ArtifactMetadataRetrievalException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    /**
     * Returns the core runtime extensions as a set of dependencies
     *
     * @return the extensions
     */
    private Set<Dependency> getCoreExtensions() {
        Set<Dependency> extensions = new HashSet<Dependency>();

        Dependency dependency = new Dependency("org.codehaus.fabric3", "fabric3-jdk-proxy", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-java", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-async", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-conversation-propagation", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-sca-intents", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-resource", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3", "fabric3-web", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("org.codehaus.fabric3.webapp", "fabric3-webapp-extension", runtimeVersion);
        extensions.add(dependency);

        dependency = new Dependency("javax.transaction", "com.springsource.javax.transaction", "1.1.0");
        extensions.add(dependency);

        return extensions;
    }

}
