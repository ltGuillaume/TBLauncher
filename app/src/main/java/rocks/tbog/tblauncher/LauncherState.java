package rocks.tbog.tblauncher;

import androidx.annotation.NonNull;

public class LauncherState {
    public enum AnimatedVisibility {
        HIDDEN,
        ANIM_TO_HIDDEN,
        ANIM_TO_VISIBLE,
        VISIBLE,
    }

    public enum Desktop {
        DESKTOP_SEARCH,
        DESKTOP_WIDGET,
        DESKTOP_EMPTY,
    }

    private AnimatedVisibility quickList = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility searchBar = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility resultList = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility notificationBar = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility widgetScreen = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility clearScreen = AnimatedVisibility.HIDDEN;
    private AnimatedVisibility keyboard = AnimatedVisibility.HIDDEN;

    private Desktop desktop = Desktop.DESKTOP_EMPTY;

    private static boolean isVisible(AnimatedVisibility state) {
        return state == AnimatedVisibility.ANIM_TO_VISIBLE ||
                state == AnimatedVisibility.VISIBLE;
    }

    public boolean isQuickListVisible() {
        return isVisible(quickList);
    }

    public boolean isSearchBarVisible() {
        return isVisible(searchBar);
    }

    public boolean isResultListVisible() {
        return isVisible(resultList);
    }

    public boolean isNotificationBarVisible() {
        return isVisible(notificationBar);
    }

    public boolean isWidgetScreenVisible() {
        return isVisible(widgetScreen);
    }

    public boolean isClearScreenVisible() {
        return isVisible(clearScreen);
    }

    public boolean isKeyboardVisible() {
        return isVisible(keyboard);
    }

    @NonNull
    public Desktop getDesktop() {
        return desktop;
    }

    public void setNotificationBar(@NonNull AnimatedVisibility state) {
        notificationBar = state;
    }

    public void setSearchBar(@NonNull AnimatedVisibility state) {
        searchBar = state;
    }

    public void setResultList(@NonNull AnimatedVisibility state) {
        resultList = state;
    }

    public void setQuickList(@NonNull AnimatedVisibility state) {
        quickList = state;
    }

    public void setWidgetScreen(@NonNull AnimatedVisibility state) {
        widgetScreen = state;
    }

    public void setKeyboard(@NonNull AnimatedVisibility state) {
        keyboard = state;
    }

    public void setDesktop(@NonNull Desktop mode) {
        desktop = mode;
    }
}
