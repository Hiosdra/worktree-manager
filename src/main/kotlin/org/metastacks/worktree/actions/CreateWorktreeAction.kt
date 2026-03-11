package org.metastacks.worktree.actions

import org.metastacks.worktree.services.WorktreeService
import org.metastacks.worktree.ui.CreateWorktreeDialog
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path

class CreateWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val dialog = CreateWorktreeDialog(project)
        if (dialog.showAndGet()) {
            val branchOrCommit = dialog.branchName
            val worktreePath = dialog.worktreePath
            val creationMode = dialog.creationMode
            val openAfterCreation = dialog.openAfterCreation

            val service = WorktreeService.getInstance(project)
            val result = service.createWorktree(branchOrCommit, worktreePath, creationMode)

            result.onSuccess { worktreeInfo ->
                if (openAfterCreation) {
                    openProject(worktreeInfo.path.toString())
                }
            }.onFailure { error ->
                com.intellij.openapi.ui.Messages.showErrorDialog(
                    project,
                    "Failed to create worktree: ${error.message}",
                    "Worktree Creation Failed"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && hasGitRepository(project)
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    private fun hasGitRepository(project: Project): Boolean {
        return GitRepositoryManager.getInstance(project).repositories.isNotEmpty()
    }

    private fun openProject(path: String) {
        val options = OpenProjectTask.build()
            .withForceOpenInNewFrame(true)
        ProjectUtil.openOrImport(Path.of(path), options)
    }
}
