/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fabric3.assembly;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

/**
 * Plugin that builds a Fabric3 runtime image based on a set of profiles and/or extensions. Standalone and Tomcat runtime images are supported.
 *
 * @goal fabric3-assembly
 * @phase generate-resources
 * @threadSafe
 */
@SuppressWarnings("ResultOfMethodCallIgnored")
public class Fabric3RuntimeAssemblyMojo extends AbstractMojo {
    private static final int BUFFER = 2048;
    private static final String RUNTIME_STANDALONE = "standalone";
    private static final String RUNTIME_TOMCAT = "tomcat";

    /**
     * Runtime configuration where the contributions should be copied.
     *
     * @parameter
     */
    public String contributionTarget = "vm";

    /**
     * True if non-target runtime configurations should be removed.
     *
     * @parameter
     */
    public boolean clean;

    /**
     * Directory where the runtime image is built.
     *
     * @parameter property="project.build.directory"
     * @required
     */
    public File buildDirectory;

    /**
     * Project source directory.
     *
     * @parameter property="project.build.sourceDirectory"
     * @required
     */
    public File sourceDirectory;

    /**
     * The default version of the runtime to use.
     *
     * @parameter property="RELEASE"
     */
    public String runtimeVersion;

    /**
     * Sets the runtime type, standalone or Tomcat
     *
     * @parameter
     */
    public String type = "standalone";

    /**
     * Set of profiles for the runtime.
     *
     * @parameter
     */
    public Dependency[] profiles = new Dependency[0];

    /**
     * Set of extensions for the runtime.
     *
     * @parameter
     */
    public Dependency[] extensions = new Dependency[0];

    /**
     * Set of configuration files
     *
     * @parameter
     */
    public ConfigFile[] configurationFiles = new ConfigFile[0];

    /**
     * Set of extensions to remove for the runtime.
     *
     * @parameter
     */
    public Dependency[] removeExtensions = new Dependency[0];

    /**
     * Set of contributions to install in the runtime.
     *
     * @parameter
     */
    public Dependency[] contributions = new Dependency[0];

    /**
     * Set of datasources to install in the runtime.
     *
     * @parameter
     */
    public Dependency[] datasources = new Dependency[0];

    /**
     * Set of jndi dependencies to install in the runtime.
     *
     * @parameter
     */
    public Dependency[] jndiDependencies = new Dependency[0];


    /**
     * @component
     */
    public RepositorySystem repositorySystem;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    public RepositorySystemSession session;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> projectRepositories;

    public Fabric3RuntimeAssemblyMojo() {
    }

    public void execute() throws MojoExecutionException {
        String artifactId;
        File baseDirectory = new File(buildDirectory, "image");
        baseDirectory.mkdirs();
        File rootDirectory;
        if (RUNTIME_STANDALONE.equalsIgnoreCase(type)) {
            artifactId = "runtime-standalone";
            rootDirectory = baseDirectory;
        } else if (RUNTIME_TOMCAT.equalsIgnoreCase(type)) {
            artifactId = "runtime-tomcat";
            // tomcat is installed as <tomcat home>/fabric3
            rootDirectory = new File(baseDirectory, "fabric3");
        } else {
            throw new MojoExecutionException("Invalid runtime type specified: " + type);
        }
        extractRuntime("org.fabric3", artifactId, baseDirectory);
        installProfiles(rootDirectory);
        installExtensions(rootDirectory);
        installDatasources(rootDirectory);
        installJndiDependencies(rootDirectory);
        installContributions(rootDirectory);
        installConfiguration(rootDirectory);
        removeExtensions(rootDirectory);

        if (clean) {
            cleanRuntimes(rootDirectory);
        }
    }

    /**
     * Cleans the runtime directories.
     *
     * @param rootDirectory root runtime image
     * @throws MojoExecutionException if there is an error
     */
    private void cleanRuntimes(File rootDirectory) throws MojoExecutionException {
        File runtimes = new File(rootDirectory, "runtimes");
        for (File file : runtimes.listFiles()) {
            if (file.isDirectory() && !contributionTarget.equals(file.getName())) {
                try {
                    FileHelper.forceDelete(file);
                } catch (IOException e) {
                    getLog().error(e);
                    throw new MojoExecutionException(e.getMessage());
                }
            }
        }
    }

    /**
     * Resolves and unzips the contents of a base runtime distribution to a directory.
     *
     * @param groupId       the distribution group id
     * @param artifactId    the distribution artifact id
     * @param baseDirectory the extract directory
     * @throws MojoExecutionException if there is an error extracting the distribution
     */
    private void extractRuntime(String groupId, String artifactId, File baseDirectory) throws MojoExecutionException {
        Artifact artifact = new DefaultArtifact(groupId, artifactId, "bin", "zip", runtimeVersion);
        try {
            getLog().info("Installing the Fabric3 runtime");
            ArtifactResult result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
            File source = result.getArtifact().getFile();
            extract(source, baseDirectory);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Resolves and installs a set of configured profiles by extracting their contents to a runtime image repository
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installProfiles(File rootDirectory) throws MojoExecutionException {
        for (Dependency profile : profiles) {
            String groupId = profile.getGroupId();
            String artifactId = profile.getArtifactId();
            String version = profile.getVersion();
            getLog().info("Installing profile: " + groupId + ":" + artifactId);
            Artifact artifact = new DefaultArtifact(groupId, artifactId, "bin", "zip", version);
            try {
                ArtifactResult result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
                File source = result.getArtifact().getFile();
                extract(source, rootDirectory);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Resolves and installs a set of configured extensions by copying them to a runtime image repository
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installExtensions(File rootDirectory) throws MojoExecutionException {
        for (Dependency extension : extensions) {
            String groupId = extension.getGroupId();
            String artifactId = extension.getArtifactId();
            String version = extension.getVersion();
            String type = extension.getType();
            String classifier = extension.getClassifier();
            getLog().info("Installing extension: " + groupId + ":" + artifactId);
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, type, version);
            ArtifactResult result;
            try {
                result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            File source = result.getArtifact().getFile();
            InputStream sourceStream = null;
            OutputStream targetStream = null;
            try {
                File repository = new File(rootDirectory, "extensions");
                sourceStream = new BufferedInputStream(new FileInputStream(source));
                File targetFile = new File(repository, source.getName());
                targetStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                copy(sourceStream, targetStream);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                close(targetStream);
                close(sourceStream);
            }
        }
    }

    /**
     * Installs configuration files to the server image.
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installConfiguration(File rootDirectory) throws MojoExecutionException {
        for (ConfigFile file : configurationFiles) {
            InputStream sourceStream = null;
            OutputStream targetStream = null;
            try {
                // main directory is parent of the source directory
                File source = new File(sourceDirectory.getParent(), file.getSource());
                File targetDirectory = new File(rootDirectory, file.getDestination());
                targetDirectory.mkdirs();
                sourceStream = new BufferedInputStream(new FileInputStream(source));
                File targetFile = new File(targetDirectory, source.getName());
                targetStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                copy(sourceStream, targetStream);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                close(targetStream);
                close(sourceStream);
            }
        }
    }

    /**
     * Installs contributions to the deploy directory.
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installContributions(File rootDirectory) throws MojoExecutionException {
        for (Dependency contribution : contributions) {
            String groupId = contribution.getGroupId();
            String artifactId = contribution.getArtifactId();
            String version = contribution.getVersion();
            String type = contribution.getType();
            String classifier = contribution.getClassifier();
            getLog().info("Installing contribution: " + groupId + ":" + artifactId);
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, type, version);
            ArtifactResult result;
            try {
                result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
            File source = result.getArtifact().getFile();
            InputStream sourceStream = null;
            OutputStream targetStream = null;
            try {
                File repository = new File(rootDirectory, "runtimes" + File.separator + contributionTarget + File.separatorChar + "deploy");
                repository.mkdirs();
                sourceStream = new BufferedInputStream(new FileInputStream(source));
                File targetFile = new File(repository, source.getName());
                targetStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                copy(sourceStream, targetStream);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                close(targetStream);
                close(sourceStream);
            }
        }

    }

    /**
     * Resolves and installs a set of configured datasource dependencies.
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installDatasources(File rootDirectory) throws MojoExecutionException {
        if (datasources == null || datasources.length == 0) {
            return;
        }
        File repository = new File(rootDirectory, "extensions");
        File datasourceDir = new File(repository, "datasource");
        datasourceDir.mkdirs();
        for (Dependency dependency : datasources) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();
            String type = dependency.getType();
            String classifier = dependency.getClassifier();
            getLog().info("Installing datasource library: " + groupId + ":" + artifactId);
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, type, version);
            //            Artifact artifact = artifactFactory.createArtifactWithClassifier(groupId, artifactId, version, type, classifier);
            ArtifactResult result;
            try {
                result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            File source = result.getArtifact().getFile();
            InputStream sourceStream = null;
            OutputStream targetStream = null;
            try {
                sourceStream = new BufferedInputStream(new FileInputStream(source));
                File targetFile = new File(datasourceDir, source.getName());
                targetStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                copy(sourceStream, targetStream);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                close(targetStream);
                close(sourceStream);
            }
        }
    }

    /**
     * Resolves and installs a set of configured jndi dependencies.
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error during installation
     */
    private void installJndiDependencies(File rootDirectory) throws MojoExecutionException {
        if (datasources == null || datasources.length == 0) {
            return;
        }
        File repository = new File(rootDirectory, "extensions");
        File datasourceDir = new File(repository, "jndi");
        datasourceDir.mkdirs();
        for (Dependency dependency : jndiDependencies) {
            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();
            String version = dependency.getVersion();
            String type = dependency.getType();
            String classifier = dependency.getClassifier();
            getLog().info("Installing jndi library: " + groupId + ":" + artifactId);
            Artifact artifact = new DefaultArtifact(groupId, artifactId, classifier, type, version);
            ArtifactResult result;
            try {
                result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            File source = result.getArtifact().getFile();
            InputStream sourceStream = null;
            OutputStream targetStream = null;
            try {
                sourceStream = new BufferedInputStream(new FileInputStream(source));
                File targetFile = new File(datasourceDir, source.getName());
                targetStream = new BufferedOutputStream(new FileOutputStream(targetFile));
                copy(sourceStream, targetStream);
            } catch (IOException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            } finally {
                close(targetStream);
                close(sourceStream);
            }
        }
    }

    /**
     * Removes extensions from the server image.
     *
     * @param rootDirectory the top-level runtime image directory
     * @throws MojoExecutionException if there is an error
     */
    private void removeExtensions(File rootDirectory) throws MojoExecutionException {
        for (Dependency extension : removeExtensions) {
            String id = extension.getArtifactId();
            String version = extension.getVersion();
            if (version == null) {
                throw new MojoExecutionException("Version not specified for: " + id);
            }
            String fileName = id + "-" + version + ".jar";
            File extensionsDir = new File(rootDirectory, "extensions");
            File file = new File(extensionsDir, fileName);
            boolean result = file.delete();
            if (!result) {
                throw new MojoExecutionException("Unable to exclude: " + file);
            }
        }
    }

    /**
     * Extracts the contents of a zip file to a target directory.
     *
     * @param source      the zip file
     * @param destination the target directory
     * @throws MojoExecutionException if there is an error during extraction
     */
    private void extract(File source, File destination) throws MojoExecutionException {
        ZipFile zipfile;
        try {
            zipfile = new ZipFile(source);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
        Enumeration enumeration = zipfile.entries();
        while (enumeration.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) enumeration.nextElement();
            if (entry.isDirectory()) {
                new File(destination, entry.getName()).mkdirs();
            } else {
                if (entry.getName().toUpperCase().endsWith(".MF")) {
                    // ignore manifests
                    continue;
                }
                InputStream sourceStream = null;
                OutputStream targetStream = null;
                try {
                    sourceStream = new BufferedInputStream(zipfile.getInputStream(entry));
                    FileOutputStream fos = new FileOutputStream(new File(destination, entry.getName()));
                    targetStream = new BufferedOutputStream(fos, BUFFER);
                    copy(sourceStream, targetStream);
                    targetStream.flush();
                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                } finally {
                    close(targetStream);
                    close(sourceStream);
                }
            }
        }
    }

    private int copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    private void close(Closeable closeable) {
        try {
            if (closeable != null) {
                closeable.close();
            }
        } catch (IOException e) {
            getLog().error(e);
        }
    }
}
