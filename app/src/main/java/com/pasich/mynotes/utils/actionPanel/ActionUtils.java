package com.pasich.mynotes.utils.actionPanel;

import android.view.View;

import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.ActionPanelBinding;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class ActionUtils {
    private boolean actionOn = false;
    private ActionPanelBinding binding;
    private View mViewRoot;
    private ManagerViewAction managerViewAction;

    @Inject
    public ActionUtils() {
    }

    public void setMangerView(View view) {
        this.mViewRoot = view;
        this.managerViewAction = (ManagerViewAction) mViewRoot.getContext();
        this.binding = ActionPanelBinding.bind(view.findViewById(R.id.actionInclude));
    }


    /**
     * @return - Returns the value of actionOn
     */
    public boolean getAction() {
        return actionOn;
    }

    /**
     * Set value to actionOn
     *
     * @param arg - (boolean) true/false
     */
    public void setAction(boolean arg) {
        actionOn = arg;
    }

    private void setListener() {
        binding.actionClose.setOnClickListener(v -> closeActionPanel());
        binding.actionDelete.setOnClickListener(v -> managerViewAction.deleteNotes());
        binding.actionShare.setOnClickListener(v -> managerViewAction.shareNotes());
        binding.actionRestore.setOnClickListener(v -> managerViewAction.restoreNotes());
    }

    public void setTrash() {
        binding.actionShare.setVisibility(View.GONE);
        binding.actionDelete.setVisibility(View.GONE);
        binding.actionRestore.setVisibility(View.VISIBLE);
    }

    /**
     * Activate the visibility of the action panel
     */
    private void activateActionPanel() {
        managerViewAction.activateActionPanel();
        mViewRoot.findViewById(R.id.actionInclude).setVisibility(View.VISIBLE);
        setListener();
    }

    /**
     * Deactivate the visibility of the action panel
     */
    private void deactivationActionPanel() {
        managerViewAction.deactivationActionPanel();
        mViewRoot.findViewById(R.id.actionInclude).setVisibility(View.GONE);
    }

    /**
     * The method that controls the visibility of the action panel
     */
    public void manageActionPanel(int countChecked) {
        if (countChecked == 0) deactivationActionPanel();
        else if (!getAction() || countChecked == 1) activateActionPanel();
    }

    public void closeActionPanel() {
        clearListeners();
        managerViewAction.toolCleanChecked();
        deactivationActionPanel();
        setAction(false);
    }

    /**
     * Очистка всех listeners
     */
    private void clearListeners() {
        if (binding != null) {
            binding.actionClose.setOnClickListener(null);
            binding.actionDelete.setOnClickListener(null);
            binding.actionShare.setOnClickListener(null);
            binding.actionRestore.setOnClickListener(null);
        }
    }

    /**
     * Должна вызываться в onDestroy активности
     */
    public void cleanup() {
        clearListeners();
        binding = null;
        mViewRoot = null;
        managerViewAction = null;
        setAction(false);
    }
}
