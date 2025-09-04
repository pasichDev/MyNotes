package com.pasich.mynotes.utils.actionPanel;

import android.view.View;

import com.pasich.mynotes.R;
import com.pasich.mynotes.databinding.ActionPanelBinding;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class ActionUtils {
    private static boolean ACTION_ON = false;
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
     * @return - Returns the value of ACTION_ON
     */
    public static boolean getAction() {
        return ACTION_ON;
    }

    /**
     * Set value to ACTION_ON
     *
     * @param arg - (boolean) true/false
     */
    public static void setAction(boolean arg) {
        ACTION_ON = arg;
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
        binding.actionClose.setOnClickListener(null);
        binding.actionDelete.setOnClickListener(null);
        binding.actionShare.setOnClickListener(null);
        binding.actionRestore.setOnClickListener(null);
        managerViewAction.toolCleanChecked();
        deactivationActionPanel();
        setAction(false);
    }
}
