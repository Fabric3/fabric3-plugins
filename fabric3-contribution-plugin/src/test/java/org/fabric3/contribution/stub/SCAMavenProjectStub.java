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
*/
package org.fabric3.contribution.stub;

import java.util.HashSet;
import java.util.LinkedList;

import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

/*
* This is a stub class for the maven project. Attempting to extend MavenProjectStub didn't
* work. A real POM could be read in during each test but since we are testing only
* the mojo it doesn't make sense. This class fills in the stub so the clone operations succeed.
*/
public class SCAMavenProjectStub extends MavenProject {

    public SCAMavenProjectStub(Model model) {
        super(model);
        super.setDependencyArtifacts(new HashSet());
        super.setArtifacts(new HashSet());
        super.setPluginArtifacts(new HashSet());
        super.setReportArtifacts(new HashSet());
        super.setExtensionArtifacts(new HashSet());
        super.setRemoteArtifactRepositories(new LinkedList());
        super.setPluginArtifactRepositories(new LinkedList());
        super.setCollectedProjects(new LinkedList());
        super.setActiveProfiles(new LinkedList());
        //super.setOriginalModel( model );
        super.setOriginalModel(null);
        super.setExecutionProject(this);
    }

}
