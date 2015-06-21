package com.intellij.idea.plugin.hybris.project.wizard;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.ElementsChooser;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.idea.plugin.hybris.project.AbstractHybrisProjectImportBuilder;
import com.intellij.idea.plugin.hybris.project.settings.HybrisModuleDescriptor;
import com.intellij.idea.plugin.hybris.utils.HybrisI18NBundleUtils;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.projectImport.SelectImportedProjectsStep;
import com.intellij.util.ArrayUtil;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Vlad Bozhenok <vladbozhenok@gmail.com>
 */
public class SelectHybrisImportedProjectsStep extends SelectImportedProjectsStep<HybrisModuleDescriptor> {

    public SelectHybrisImportedProjectsStep(final WizardContext context) {
        super(context);

        this.fileChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<HybrisModuleDescriptor>() {

            @Override
            public void elementMarkChanged(final HybrisModuleDescriptor element, final boolean isMarked) {
                if (isMarked) {
                    for (HybrisModuleDescriptor moduleDescriptor : element.getDependenciesPlainList()) {
                        if (fileChooser.isElementMarked(moduleDescriptor)) {
                            continue;
                        }

                        fileChooser.setElementMarked(moduleDescriptor, true);
                    }
                }

                fileChooser.repaint();
            }
        });
    }

    @Override
    @Nullable
    protected Icon getElementIcon(final HybrisModuleDescriptor item) {
        if (this.isInConflict(item)) {
            return AllIcons.Actions.Cancel;
        } else if (this.getContext().getHybrisProjectDescriptor().getAlreadyOpenedModules().contains(item)) {
            return AllIcons.General.InspectionsOK;
        }

        return null;
    }

    protected boolean isInConflict(@NotNull final HybrisModuleDescriptor item) {
        Validate.notNull(item);

        return this.fileChooser.getMarkedElements().contains(item)
               && this.calculateSelectedModuleDuplicates().contains(item);
    }

    @Override
    public AbstractHybrisProjectImportBuilder getContext() {
        return (AbstractHybrisProjectImportBuilder) this.getBuilder();
    }

    @NotNull
    protected Set<HybrisModuleDescriptor> calculateSelectedModuleDuplicates() {
        final Set<HybrisModuleDescriptor> duplicateModules = new HashSet<HybrisModuleDescriptor>();
        final Map<String, HybrisModuleDescriptor> uniqueModules = new HashMap<String, HybrisModuleDescriptor>();

        for (HybrisModuleDescriptor moduleDescriptor : this.fileChooser.getMarkedElements()) {

            final HybrisModuleDescriptor alreadySelected = uniqueModules.get(moduleDescriptor.getModuleName());

            if (null == alreadySelected) {
                uniqueModules.put(moduleDescriptor.getModuleName(), moduleDescriptor);
            } else {
                duplicateModules.add(alreadySelected);
                duplicateModules.add(moduleDescriptor);
            }
        }

        return duplicateModules;
    }

    @Override
    protected String getElementText(final HybrisModuleDescriptor item) {

        final StringBuilder builder = new StringBuilder();

        builder.append(item.getModuleName());
        builder.append("         (");
        builder.append(item.getModuleRelativePath());
        builder.append(')');

        return builder.toString();
    }

    @Override
    public boolean validate() throws ConfigurationException {
        final Set<HybrisModuleDescriptor> moduleDuplicates = this.calculateSelectedModuleDuplicates();
        final Collection<String> moduleDuplicateNames = new ArrayList<String>(moduleDuplicates.size());

        for (HybrisModuleDescriptor moduleDuplicate : moduleDuplicates) {
            moduleDuplicateNames.add(this.getModuleNameAndPath(moduleDuplicate));
        }

        if (!moduleDuplicates.isEmpty()) {
            throw new ConfigurationException(
                HybrisI18NBundleUtils.message(
                    "hybris.project.import.duplicate.projects.found",
                    StringUtil.join(ArrayUtil.toStringArray(moduleDuplicateNames), "\n")
                ),
                HybrisI18NBundleUtils.message("hybris.project.error")
            );
        }

        return super.validate();
    }

    @NotNull
    private String getModuleNameAndPath(@NotNull final HybrisModuleDescriptor moduleDescriptor) {
        Validate.notNull(moduleDescriptor);

        final StringBuilder builder = new StringBuilder();

        builder.append(moduleDescriptor.getModuleName());
        builder.append(' ');
        builder.append('(');
        builder.append(moduleDescriptor.getModuleRelativePath());
        builder.append(')');

        return builder.toString();
    }
}
