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
package org.fabric3.packager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
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
 * Plugin that assembles a Fabric3 node runtime including extensions for deployment in WAR.
 *
 * @goal fabric3-packager
 * @phase generate-resources
 * @threadSafe
 */
@SuppressWarnings("JavaDoc")
public class Fabric3PackagerMojo extends AbstractMojo {
    public static final String F3_ARTIFACT_ID = "org.fabric3";
    public static final String F3_EXTENSIONS_JAR = "f3.extensions.jar";
    private static final int BUFFER = 2048;

    /**
     * Directory where the app is built.
     *
     * @parameter property="project.build.directory"
     * @required
     */
    public File buildDirectory;

    /**
     * WAR name.
     *
     * @parameter default-value="${project.build.finalName}" expression="${war.warName}"
     * @required
     */
    public String warName;

    /**
     * The default version of the runtime to use.
     *
     * @parameter property="RELEASE"
     */
    public String runtimeVersion;

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


    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void execute() throws MojoExecutionException {

        addDefaultExtensions();

        File libDirectory = new File(buildDirectory, warName + File.separator + "WEB-INF" + File.separator + "lib");
        libDirectory.mkdirs();

        File stagingDirectory = new File(buildDirectory, "f3");
        stagingDirectory.mkdirs();

        File extensionsDirectory = new File(stagingDirectory, "extensions");
        extensionsDirectory.mkdir();

        extractProfiles(stagingDirectory);
        resolveDependencies(extensions, extensionsDirectory);

        createExtensionsArchive(extensionsDirectory, libDirectory);

        Dependency[] dependencies = new Dependency[2];

        dependencies[0] = new Dependency();
        dependencies[0].setGroupId(F3_ARTIFACT_ID);
        dependencies[0].setArtifactId("fabric3-node");
        dependencies[0].setVersion(runtimeVersion);

        dependencies[1] = new Dependency();
        dependencies[1].setGroupId(F3_ARTIFACT_ID);
        dependencies[1].setArtifactId("fabric3-node-extensions");
        dependencies[1].setVersion(runtimeVersion);

        resolveDependencies(dependencies, libDirectory);
    }

    private void addDefaultExtensions() {
        int length = extensions.length;
        extensions = Arrays.copyOf(extensions, length + 2);

        extensions[length] = new Dependency();
        extensions[length].setGroupId(F3_ARTIFACT_ID);
        extensions[length].setArtifactId("fabric3-databinding-json");
        extensions[length].setVersion(runtimeVersion);
    }

    /**
     * Resolves configured profiles and extracting their contents to the temporary extensions directory.
     *
     * @param extensionsDirectory the extensions directory
     * @throws MojoExecutionException if there is an error during extraction
     */
    private void extractProfiles(File extensionsDirectory) throws MojoExecutionException {
        for (Dependency profile : profiles) {
            String groupId = profile.getGroupId();
            String artifactId = profile.getArtifactId();
            String version = profile.getVersion();
            getLog().info("Resolving profile: " + groupId + ":" + artifactId);

            Artifact artifact = new DefaultArtifact(groupId, artifactId, "bin", "zip", version);
            try {
                ArtifactResult result = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, projectRepositories, null));
                File source = result.getArtifact().getFile();
                extract(source, extensionsDirectory);
            } catch (ArtifactResolutionException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
    }

    /**
     * Resolves a dependency to the target directory.
     *
     * @param targetDirectory the target directory
     * @throws MojoExecutionException if there is an error during resolution
     */
    private void resolveDependencies(Dependency[] dependencies, File targetDirectory) throws MojoExecutionException {
        for (Dependency extension : dependencies) {
            String groupId = extension.getGroupId();
            String artifactId = extension.getArtifactId();
            String version = extension.getVersion();
            String type = extension.getType();
            String classifier = extension.getClassifier();
            getLog().info("Resolving dependency: " + groupId + ":" + artifactId);
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
     * Extracts the contents of a zip file to a target directory.
     *
     * @param source      the zip file
     * @param destination the target directory
     * @throws MojoExecutionException if there is an error during extraction
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
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

    private void createExtensionsArchive(File extensionsDirectory, File libDirectory) throws MojoExecutionException {
        JarOutputStream jarStream = null;
        try {
            File archive = new File(libDirectory, F3_EXTENSIONS_JAR);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(archive));
            jarStream = new JarOutputStream(os);
            for (File file : extensionsDirectory.listFiles()) {
                if (!file.getName().endsWith(".jar")) {
                    continue;
                }
                JarEntry entry = new JarEntry(file.getName());
                jarStream.putNextEntry(entry);
                copy(new FileInputStream(file), jarStream);
            }
            jarStream.flush();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            close(jarStream);
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
