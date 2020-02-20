package com.mathworks.ci;

/*
 * Copyright 2020-2021 The MathWorks, Inc. Build Tester is used for unit testing to mock the actual
 * build.
 * 
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import net.sf.json.JSONObject;

public class MatlabScriptBuilderTester extends MatlabScriptBuilder {
    private int buildResult;
    private EnvVars env;
    private MatlabReleaseInfo matlabRel;
    private String matlabCommand;
    private String commandParameter;
    private String matlabExecutorPath;

    public MatlabScriptBuilderTester(String matlabExecutorPath, String customTestPointArgument) {
        super();
        this.commandParameter = customTestPointArgument;
        this.matlabExecutorPath = matlabExecutorPath;
    }


    // Getter and Setters to access local members

    public void setMatlabCommand(String matlabCommand) {
        this.matlabCommand = matlabCommand;
    }

    private void setEnv(EnvVars env) {
        this.env = env;
    }


    @Extension
    public static class Desriptor extends BuildStepDescriptor<Builder> {
        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }

        @Override
        public boolean isApplicable(
                @SuppressWarnings("rawtypes") Class<? extends AbstractProject> jobtype) {
            return true;
        }
    }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace,
            @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException {

        // Set the environment variable specific to the this build
        setEnv(build.getEnvironment(listener));
        String matlabRoot = this.env.get("matlabroot");

        // Get node specific matlabroot to get matlab version information
        FilePath nodeSpecificMatlabRoot = new FilePath(launcher.getChannel(), matlabRoot);
        matlabRel = new MatlabReleaseInfo(nodeSpecificMatlabRoot);

        // Invoke MATLAB command and transfer output to standard
        // Output Console

        buildResult = execMatlabCommand(workspace, launcher, listener);

        if (buildResult != 0) {
            build.setResult(Result.FAILURE);
        }
    }

    private int execMatlabCommand(FilePath workspace, Launcher launcher,
            TaskListener listener) throws IOException, InterruptedException {
        ProcStarter matlabLauncher;
        try {
            matlabLauncher = launcher.launch().pwd(workspace).envs(this.env);
            if (matlabRel.verLessThan(MatlabBuilderConstants.BASE_MATLAB_VERSION_BATCH_SUPPORT)) {
                ListenerLogDecorator outStream = new ListenerLogDecorator(listener);
                matlabLauncher = matlabLauncher.cmds(testMatlabCommand()).stderr(outStream);
            } else {
                matlabLauncher = matlabLauncher.cmds(testMatlabCommand()).stdout(listener);
            }

        } catch (Exception e) {
            listener.getLogger().println(e.getMessage());
            return 1;
        }
        return matlabLauncher.join();
    }

    // Private custom method to pass mock MATLAB with parameters.
    private List<String> testMatlabCommand() {
        List<String> matlabDefaultArgs = new ArrayList<String>();
        matlabDefaultArgs.add(this.matlabExecutorPath);
        matlabDefaultArgs.add(this.commandParameter);
        return matlabDefaultArgs;
    }
}
