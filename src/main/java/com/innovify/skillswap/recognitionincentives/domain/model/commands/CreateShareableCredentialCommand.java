package com.innovify.skillswap.recognitionincentives.domain.model.commands;

/** The student asks for the public link of a verified skill. */
public record CreateShareableCredentialCommand(int userId, String skillTag) {
}
