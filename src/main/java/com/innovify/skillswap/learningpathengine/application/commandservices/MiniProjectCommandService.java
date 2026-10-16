package com.innovify.skillswap.learningpathengine.application.commandservices;

import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateMiniProjectCommand;
import com.innovify.skillswap.shared.application.Result;

/** Mini-project command service interface. */
public interface MiniProjectCommandService {

    /**
     * Generates with AI the mini-project of an available node (US19). While the latest mini-project of the node can
     * still be delivered it is returned instead, so the student does not spend another generation, unless the command
     * asks to regenerate it. Fails with NODE_NOT_FOUND, NOT_PATH_OWNER, PATH_PAUSED, NODE_LOCKED,
     * NODE_ALREADY_COMPLETED or MINI_PROJECT_GENERATION_FAILED.
     */
    Result<MiniProjectGeneration> handle(GenerateMiniProjectCommand command);
}
