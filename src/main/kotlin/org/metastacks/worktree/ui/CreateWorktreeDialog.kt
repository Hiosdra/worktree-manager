package org.metastacks.worktree.ui

import org.metastacks.worktree.services.WorktreeService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRepositoryManager
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

enum class WorktreeCreationMode {
    CREATE_NEW_BRANCH,
    CHECKOUT_EXISTING_BRANCH,
    DETACHED_HEAD
}

class CreateWorktreeDialog(private val project: Project) : DialogWrapper(project) {

    private val existingBranchRadio = JBRadioButton("Checkout existing branch", false)
    private val newBranchRadio = JBRadioButton("Create new branch", true)
    private val detachedHeadRadio = JBRadioButton("Detached HEAD (from commit/tag)", false)

    private val branchComboBox = com.intellij.openapi.ui.ComboBox<String>()
    private val newBranchField = JBTextField(20)
    private val commitishField = JBTextField(20)
    private val pathField = TextFieldWithBrowseButton()
    private val openAfterCreationCheckbox = JBCheckBox("Open in IDE after creation", true)

    private val branchLabel = JBLabel("Branch:")
    private val newBranchLabel = JBLabel("New branch name:")
    private val commitishLabel = JBLabel("Commit/tag:")

    private val worktreeService = WorktreeService.getInstance(project)

    val creationMode: WorktreeCreationMode
        get() = when {
            newBranchRadio.isSelected -> WorktreeCreationMode.CREATE_NEW_BRANCH
            existingBranchRadio.isSelected -> WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH
            detachedHeadRadio.isSelected -> WorktreeCreationMode.DETACHED_HEAD
            else -> WorktreeCreationMode.CREATE_NEW_BRANCH
        }

    val branchName: String
        get() = when (creationMode) {
            WorktreeCreationMode.CREATE_NEW_BRANCH -> newBranchField.text
            WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH -> branchComboBox.selectedItem as? String ?: ""
            WorktreeCreationMode.DETACHED_HEAD -> commitishField.text
        }

    val worktreePath: Path
        get() = Path.of(pathField.text)

    val openAfterCreation: Boolean
        get() = openAfterCreationCheckbox.isSelected

    init {
        title = "Create Worktree"
        init()
        loadBranches()
        setupListeners()
        updateVisibility()
    }

    override fun createCenterPanel(): JComponent {
        ButtonGroup().apply {
            add(newBranchRadio)
            add(existingBranchRadio)
            add(detachedHeadRadio)
        }

        pathField.addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Worktree Location")
                .withDescription("Choose the directory for the new worktree")
        )

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }

        // Radio buttons
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2
        panel.add(newBranchRadio, gbc)

        gbc.gridy = 1
        panel.add(existingBranchRadio, gbc)

        gbc.gridy = 2
        panel.add(detachedHeadRadio, gbc)

        // New branch name
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(newBranchLabel, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(newBranchField, gbc)

        // Branch selection
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(branchLabel, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(branchComboBox, gbc)

        // Commit-ish field
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(commitishLabel, gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(commitishField, gbc)

        // Path
        gbc.gridx = 0; gbc.gridy = 6; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JBLabel("Path:"), gbc)

        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(pathField, gbc)

        // Checkbox
        gbc.gridx = 0; gbc.gridy = 7; gbc.gridwidth = 2
        panel.add(openAfterCreationCheckbox, gbc)

        // Set preferred width (~30% wider than default)
        panel.preferredSize = Dimension(550, panel.preferredSize.height)

        return panel
    }

    private fun loadBranches() {
        val repositories = GitRepositoryManager.getInstance(project).repositories
        if (repositories.isEmpty()) return

        val repository = repositories.first()
        val branches = GitBranchUtil.sortBranchNames(repository.branches.localBranches.map { it.name })

        branchComboBox.removeAllItems()
        branches.forEach { branchComboBox.addItem(it) }
    }

    private fun setupListeners() {
        // Update visibility when radio buttons change
        newBranchRadio.addActionListener {
            updateVisibility()
            updateDefaultPath()
        }
        existingBranchRadio.addActionListener {
            updateVisibility()
            updateDefaultPath()
        }
        detachedHeadRadio.addActionListener {
            updateVisibility()
            updateDefaultPath()
        }

        // Update path when branch changes
        branchComboBox.addActionListener { updateDefaultPath() }
        newBranchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
        })
        commitishField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
        })

        // Set initial path
        updateDefaultPath()
    }

    private fun updateVisibility() {
        when (creationMode) {
            WorktreeCreationMode.CREATE_NEW_BRANCH -> {
                newBranchLabel.isVisible = true
                newBranchField.isVisible = true
                branchLabel.isVisible = false
                branchComboBox.isVisible = false
                commitishLabel.isVisible = false
                commitishField.isVisible = false
            }
            WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH -> {
                newBranchLabel.isVisible = false
                newBranchField.isVisible = false
                branchLabel.isVisible = true
                branchComboBox.isVisible = true
                commitishLabel.isVisible = false
                commitishField.isVisible = false
            }
            WorktreeCreationMode.DETACHED_HEAD -> {
                newBranchLabel.isVisible = false
                newBranchField.isVisible = false
                branchLabel.isVisible = false
                branchComboBox.isVisible = false
                commitishLabel.isVisible = true
                commitishField.isVisible = true
            }
        }
    }

    private fun updateDefaultPath() {
        val branch = when (creationMode) {
            WorktreeCreationMode.CREATE_NEW_BRANCH -> newBranchField.text
            WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH -> branchComboBox.selectedItem as? String
            WorktreeCreationMode.DETACHED_HEAD -> commitishField.text
        }
        if (!branch.isNullOrBlank()) {
            val defaultPath = worktreeService.getDefaultWorktreePath(branch)
            if (defaultPath != null) {
                pathField.text = defaultPath.toString()
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        val branch = branchName
        if (branch.isBlank()) {
            val errorComponent = when (creationMode) {
                WorktreeCreationMode.CREATE_NEW_BRANCH -> newBranchField
                WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH -> branchComboBox
                WorktreeCreationMode.DETACHED_HEAD -> commitishField
            }
            val errorMessage = when (creationMode) {
                WorktreeCreationMode.CREATE_NEW_BRANCH -> "Branch name is required"
                WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH -> "Branch selection is required"
                WorktreeCreationMode.DETACHED_HEAD -> "Commit/tag is required"
            }
            return ValidationInfo(errorMessage, errorComponent)
        }

        val path = pathField.text
        if (path.isBlank()) {
            return ValidationInfo("Path is required", pathField)
        }

        val pathFile = Path.of(path)
        if (java.nio.file.Files.exists(pathFile)) {
            return ValidationInfo("Path already exists", pathField)
        }

        // Check if branch already has a worktree (only for existing branch mode)
        if (creationMode == WorktreeCreationMode.CHECKOUT_EXISTING_BRANCH) {
            val existingWorktrees = worktreeService.listWorktrees()
            val branchInUse = existingWorktrees.find { it.branch == branch }
            if (branchInUse != null) {
                return ValidationInfo(
                    "Branch '$branch' is already checked out in: ${branchInUse.path}",
                    branchComboBox
                )
            }
        }

        return null
    }
}
