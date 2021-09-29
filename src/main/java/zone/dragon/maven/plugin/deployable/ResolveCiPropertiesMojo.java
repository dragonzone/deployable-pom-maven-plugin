/*
 * Copyright 2021 Bryan Harclerode
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package zone.dragon.maven.plugin.deployable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.building.ModelProblemCollectorRequest;
import org.apache.maven.model.interpolation.StringVisitorModelInterpolator;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.NoSuchRealmException;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.codehaus.plexus.interpolation.ValueSource;

/**
 * @author Bryan Harclerode
 * @date 2/21/2021
 */
@Mojo(name = "resolve-ci-properties", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class ResolveCiPropertiesMojo extends AbstractMojo {

    /**
     * The Maven Project
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * The Maven Session
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * The Maven Plugin
     */
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor plugin;

    /**
     * Location where the updated pom.xml should be written
     */
    @Parameter(defaultValue = "${project.build.directory}/deployable-pom.xml")
    private File deployablePomFile;

    @Override
    public void execute() throws MojoExecutionException {
        // Need to access ValueSource and descendants from plexus interpolation
        importFrom("plexus.core", "org.codehaus.plexus.interpolation");

        Model model = readModel();
        model = interpolateCiProperties(model);
        writeModel(model);
        getLog().info("Resolved CI properties in " + deployablePomFile.getAbsolutePath());
    }

    protected void importFrom(String realmId, String packageName) throws MojoExecutionException {
        ClassRealm realm = plugin.getClassRealm();
        try {
            getLog().debug("Importing additional classes from " + realmId + ":" + packageName);
            realm.importFrom(realmId, packageName);
        } catch (NoSuchRealmException e) {
            throw new MojoExecutionException("Unable to import required realm: " + realmId, e);
        }
    }

    protected Model readModel() throws MojoExecutionException {
        try {
            getLog().debug("Loading pom file from " + project.getFile().getAbsolutePath());
            DefaultModelReader reader = new DefaultModelReader();
            return reader.read(project.getFile(), Collections.emptyMap());
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read pom file: " + project.getFile().getAbsolutePath(), e);
        }
    }

    protected void writeModel(Model model) throws MojoExecutionException {
        try {
            getLog().debug("Writing deployable pom file to " + deployablePomFile);
            DefaultModelWriter writer = new DefaultModelWriter();
            writer.write(deployablePomFile, Collections.emptyMap(), model);
            project.setPomFile(deployablePomFile);
            getLog().debug("deployable pom file written.");
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write deployable pom file: " + project.getFile().getAbsolutePath(), e);
        }
    }

    protected Model interpolateCiProperties(Model model) throws MojoExecutionException {
        CiPropertyVisitorModelInterpolator interpolator = new CiPropertyVisitorModelInterpolator();
        return interpolator.interpolateModel(model);
    }

    private class CiPropertyValueSource extends AbstractValueSource {

        public CiPropertyValueSource() {
            super(false);
        }

        @Override
        public Object getValue(String expression) {
            if ("sha1".equals(expression) || "revision".equals(expression) || "changelist".equals(expression)) {
                return getProperty(expression);
            }
            return null;
        }

        private String getProperty(String name) {
            return session
                .getUserProperties()
                .getProperty(name, session.getSystemProperties().getProperty(name, project.getProperties().getProperty(name, "")));
        }
    }

    private class CiPropertyVisitorModelInterpolator extends StringVisitorModelInterpolator implements ModelProblemCollector {

        private List<ModelProblemCollectorRequest> problems = new ArrayList<>();

        @Override
        protected List<ValueSource> createValueSources(
            Model model, File projectDir, ModelBuildingRequest config, ModelProblemCollector problems
        ) {
            return Collections.singletonList(new CiPropertyValueSource());
        }

        @Override
        protected List<? extends InterpolationPostProcessor> createPostProcessors(
            Model model, File projectDir, ModelBuildingRequest config
        ) {
            return Collections.emptyList();
        }

        public Model interpolateModel(Model model) throws MojoExecutionException {
            try {
                return interpolateModel(model, null, null, this);
            } finally {
                if (problems.size() == 1) {
                    ModelProblemCollectorRequest req = problems.get(1);
                    throw new MojoExecutionException("Failed to interpolate CI properties: " + req.getMessage(), req.getException());
                } else if (problems.size() > 1) {

                }
            }
        }

        @Override
        public void add(ModelProblemCollectorRequest req) {
            switch (req.getSeverity()) {
                case WARNING:
                    getLog().warn(req.getMessage(), req.getException());
                    break;
                case ERROR:
                    getLog().error(req.getMessage(), req.getException());
                    break;
                case FATAL:
                    problems.add(req);
            }
        }
    }
}
