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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.plugin.testing.stubs.ArtifactStub;

/*
 * Represents a maven artifact that is referenced or generated during the build
 */
public class SCAArtifactStub extends ArtifactStub {

    public SCAArtifactStub() {
        super();
    }

    public String getVersion() {
        if (super.getVersion() == null) {
            super.setVersion("0.0-Test");
        }
        return super.getVersion();
    }

    public String getArtifactId() {
        if (super.getArtifactId() == null) {
            super.setArtifactId("sca-contribution-plugin-test");
        }
        return super.getArtifactId();
    }

    public String getGroupId() {
        if (super.getGroupId() == null) {
            super.setGroupId("org.codehaus.fabric3");
        }
        return super.getGroupId();
    }

    public String getClassifier() {
        return super.getClassifier();
    }

    public String getScope() {
        if (super.getScope() == null) {
            super.setScope(Artifact.SCOPE_RUNTIME);
        }
        return super.getScope();
    }

    public boolean isOptional() {
        return super.isOptional();
    }

    public String getType() {
        if (super.getType() == null) {
            super.setType("sca-contribution");
        }
        return super.getType();
    }

    public ArtifactHandler getArtifactHandler() {
        return new DefaultArtifactHandler(getType());
    }

    public VersionRange getVersionRange() {
        return VersionRange.createFromVersion(getVersion());
    }
}
