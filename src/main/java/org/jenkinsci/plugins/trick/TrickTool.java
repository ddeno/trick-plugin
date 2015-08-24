package org.jenkinsci.plugins.trick;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.init.Initializer;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import java.util.logging.Level;

/**
 * Information about Trick installation. A TrickTool is used to select
 * between different installations of trick, as in "trick-13.5.0" or "trick-15.0.0".
 *
 * @author Drake Deno
 */
public class TrickTool extends ToolInstallation implements NodeSpecific<TrickTool>, EnvironmentSpecific<TrickTool> {

    /**
     * Constructor for TrickTool.
     *
     * @param name Tool name (for example, "trick-13.5.0" or "15.0.0")
     * @param home Tool location
     * @param properties {@link java.util.List} of properties for this tool
     */
    @DataBoundConstructor
    public TrickTool(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    /** Constant <code>DEFAULT="Default"</code> */
    public static transient final String DEFAULT = "trick";

    private static final long serialVersionUID = 1;

    /** Global Trick environment variable settings */
    public boolean m_global_env_vars = false;

    /** Global Trick C flags */
    public String m_global_cflags = "";

    /** Global Trick C++ flags */
    public String m_global_cxxflags = "";

    /** Global Trick User Link Libs */
    public String m_global_user_link_libs = "";

    /** Global Trick Debug Flag */
    public boolean m_global_debug = false;

    /**
     * getTrickHome.
     *
     * @return {@link java.lang.String} that will be used to reference Trick home (e.g. "/usr/local/trick/13.5.0")
     */
    public String getTrickHome() {
        return getHome();
    }

    /**
     * useGlobalEnvVars
     *
     * @return {@link java.lang.Boolean} that will be used to let the user set global environment variables for
     *          specific Trick tool installations.
     */
    public Boolean useGlobalEnvVars() {
        return m_global_env_vars;
    }

    /**
     * getGlobalCFlags
     *
     * @return {@link java.lang.String} that will be used to prepend to Trick build step CFLAGS.
     */
    public String getGlobalCFlags() {
        return m_global_cflags;
    }

    /**
     * getGlobalCxxFlags
     *
     * @return {@link java.lang.String} that will be used to prepend to Trick build step CXXFLAGS.
     */
    public String getGlobalCxxFlags() {
        return m_global_cxxflags;
    }

    /**
     * getGlobalUserLinkLibs
     *
     * @return {@link java.lang.String} that will be used to prepend to Trick build step TRICK_USER_LINK_LIBS.
     */
    public String getGlobalUserLinkLibs() {
        return m_global_user_link_libs;
    }

    /**
     * getGlobalDebug
     *
     * @return {@link java.lang.Boolean} that will be used to set the Trick debug flag globally.
     */
    public Boolean getGlobalDebug() {
        return m_global_debug;
    }

    private static TrickTool[] getInstallations(DescriptorImpl descriptor) {
        TrickTool[] installations = null;
        try {
            installations = descriptor.getInstallations();
        } catch (NullPointerException e) {
            installations = new TrickTool[0];
        }
        return installations;
    }

    /**
     * Returns the default installation.
     *
     * @return default installation
     */
    public static TrickTool getDefaultInstallation() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            return null;
        }
        DescriptorImpl trickTools = jenkinsInstance.getDescriptorByType(TrickTool.DescriptorImpl.class);
        TrickTool tool = trickTools.getInstallation(TrickTool.DEFAULT);
        if (tool != null) {
            return tool;
        } else {
            TrickTool[] installations = trickTools.getInstallations();
            if (installations.length > 0) {
                return installations[0];
            } else {
                onLoaded();
                return trickTools.getInstallations()[0];
            }
        }
    }

    public TrickTool forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new TrickTool(getName(), translateFor(node, log), Collections.<ToolProperty<?>>emptyList());
    }

    public TrickTool forEnvironment(EnvVars environment) {
        return new TrickTool(getName(), environment.expand(getHome()), Collections.<ToolProperty<?>>emptyList());
    }

    @Override
    public DescriptorImpl getDescriptor() {
        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            /* Throw AssertionError exception to match behavior of Jenkins.getDescriptorOrDie */
            throw new AssertionError("No Jenkins instance");
        }
        return (DescriptorImpl) jenkinsInstance.getDescriptorOrDie(getClass());
    }

    @Initializer(after=EXTENSIONS_AUGMENTED)
    public static void onLoaded() {
        //Creates default tool installation if needed. Uses "trick" or migrates data from previous versions

        Jenkins jenkinsInstance = Jenkins.getInstance();
        if (jenkinsInstance == null) {
            return;
        }
        DescriptorImpl descriptor = (DescriptorImpl) jenkinsInstance.getDescriptor(TrickTool.class);
        TrickTool[] installations = getInstallations(descriptor);

        if (installations != null && installations.length > 0) {
            //No need to initialize if there's already something
            return;
        }

        String defaultTrickLocation = "/usr/local/trick";
        TrickTool tool = new TrickTool(DEFAULT, defaultTrickLocation, Collections.<ToolProperty<?>>emptyList());
        descriptor.setInstallations(new TrickTool[] { tool });
        descriptor.save();
    }


    @Extension
    public static class DescriptorImpl extends ToolDescriptor<TrickTool> {

        public DescriptorImpl() {
            super();
            load();
        }

        @Override
        public String getDisplayName() {
            return "Trick";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            setInstallations(req.bindJSONToList(clazz, json.get("tool")).toArray(new TrickTool[0]));
            save();
            return true;
        }

        public FormValidation doCheckHome(@QueryParameter File value) {
            Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);
            String path = value.getPath();

            return FormValidation.validateExecutable(path);
        }

        public TrickTool getInstallation(String name) {
            for(TrickTool i : getInstallations()) {
                if(i.getName().equals(name)) {
                    return i;
                }
            }
            return null;
        }

        public List<ToolDescriptor<? extends TrickTool>> getApplicableDesccriptors() {
            List<ToolDescriptor<? extends TrickTool>> r = new ArrayList<ToolDescriptor<? extends TrickTool>>();
            Jenkins jenkinsInstance = Jenkins.getInstance();
            if (jenkinsInstance == null) {
                return r;
            }
            for (ToolDescriptor td : jenkinsInstance.<ToolInstallation,ToolDescriptor<?>>getDescriptorList(ToolInstallation.class)) {
                if (TrickTool.class.isAssignableFrom(td.clazz))
                    r.add(td);
            }
            return r;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(TrickTool.class.getName());
}
