package com.pasich.mynotes.utils.actionPanel.interfaces;


public interface ManagerViewAction<T> extends MangerActionTool {
    void activateActionPanel();

    void deactivationActionPanel();

    void toolCleanChecked();

    void selectItemAction(T note, int position, boolean payloads);
}
