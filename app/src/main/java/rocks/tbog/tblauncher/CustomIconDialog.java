package rocks.tbog.tblauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.collection.ArraySet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import rocks.tbog.tblauncher.icons.IconPack;
import rocks.tbog.tblauncher.icons.IconPackXML;
import rocks.tbog.tblauncher.normalizer.StringNormalizer;
import rocks.tbog.tblauncher.ui.DialogFragment;
import rocks.tbog.tblauncher.utils.DrawableUtils;
import rocks.tbog.tblauncher.utils.FuzzyScore;
import rocks.tbog.tblauncher.utils.UserHandleCompat;
import rocks.tbog.tblauncher.utils.Utilities;

public class CustomIconDialog extends DialogFragment<Drawable> {
    private final List<IconData> mIconData = new ArrayList<>();
    private Drawable mSelectedDrawable = null;
    private GridView mIconGrid;
    private TextView mSearch;
    private ImageView mPreview;
    private LinearLayout mIconPackList;
    private IconPackXML mShownIconPack;

    @Override
    protected int layoutRes() {
        return R.layout.custom_icon_dialog;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = getContext();
        assert context != null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.setClipToOutline(true);
        }

        mIconPackList = view.findViewById(R.id.iconPackList);
        populateIconPackList();

        mIconGrid = view.findViewById(R.id.iconGrid);
        IconAdapter iconAdapter = new IconAdapter(mIconData);
        mIconGrid.setAdapter(iconAdapter);

        iconAdapter.setOnItemClickListener((adapter, v, position) -> {
            mSelectedDrawable = adapter.getItem(position).getIcon();
            mPreview.setImageDrawable(mSelectedDrawable);
        });

        mSearch = view.findViewById(R.id.search);
        mSearch.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
                // Auto left-trim text.
                if (s.length() > 0 && s.charAt(0) == ' ')
                    s.delete(0, 1);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mSearch.post(() -> refreshList());
            }
        });

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String name = args.getString("componentName", "");
        long customIcon = args.getLong("customIcon", 0);

        IconsHandler iconsHandler = TBApplication.getApplication(context).getIconsHandler();
        ComponentName cn = UserHandleCompat.unflattenComponentName(name);
        UserHandleCompat userHandle = UserHandleCompat.fromComponentName(context, name);

        // Preview
        {
            mPreview = view.findViewById(R.id.preview);
//            Drawable drawable = customIcon != 0 ? iconsHandler.getCustomIcon(name, customIcon) : null;
//            if (drawable == null)
//                drawable = iconsHandler.getDrawableIconForPackage(cn, userHandle);
//            mPreview.setImageDrawable(drawable);
            Utilities.setIconAsync(mPreview, ctx -> {
                Drawable drawable = customIcon != 0 ? iconsHandler.getCustomIcon(name, customIcon) : null;
                if (drawable == null)
                    drawable = iconsHandler.getDrawableIconForPackage(cn, userHandle);
                return drawable;
            });
        }

        // OK button
        {
            View button = view.findViewById(android.R.id.button1);
            button.setOnClickListener(v -> {
                onConfirm(mSelectedDrawable);
                dismiss();
            });
        }

        // CANCEL button
        {
            View button = view.findViewById(android.R.id.button2);
            button.setOnClickListener(v -> dismiss());
        }
    }

    private void populateIconPackList() {
        mIconPackList.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(getContext());
        Map<String, String> iconPacks = TBApplication.iconsHandler(getContext()).getIconPackNames();
        for (Map.Entry<String, String> packInfo : iconPacks.entrySet()) {
            String packageName = packInfo.getKey();
            String packName = packInfo.getValue();
            View packView = inflater.inflate(R.layout.item_quick_list, mIconPackList, false);
            mIconPackList.addView(packView);

            ImageView icon = packView.findViewById(android.R.id.icon);
            TextView name = packView.findViewById(android.R.id.text1);

            name.setText(packName);

            Utilities.setIconAsync(icon, (ctx) -> {
                Drawable drawable = null;
                try {
                    drawable = ctx.getPackageManager().getApplicationIcon(packageName);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
                return drawable;
            });

            packView.setTag(packageName);
            packView.setOnClickListener((v) -> {
                String tag = v.getTag().toString();
                setShownIconPack(tag);
            });
        }

        if (mIconPackList.getChildCount() == 0)
            mIconPackList.setVisibility(View.GONE);
        else
            mIconPackList.requestLayout();
    }

    private void refreshQuickList() {
        Context context = getContext();
        View view = getView();

        Bundle args = getArguments() != null ? getArguments() : new Bundle();
        String name = args.getString("componentName", "");

        IconsHandler iconsHandler = TBApplication.getApplication(context).getIconsHandler();
        ComponentName cn = UserHandleCompat.unflattenComponentName(name);
        UserHandleCompat userHandle = UserHandleCompat.fromComponentName(context, name);

        //TODO: move this in an async task
        setQuickList(iconsHandler, view, cn, userHandle);
    }

    private void setQuickList(IconsHandler iconsHandler, View view, ComponentName cn, UserHandleCompat userHandle) {
        Context context = view.getContext();
        ViewGroup quickList = view.findViewById(R.id.quickList);
        quickList.removeViews(1, quickList.getChildCount() - 1);

        ArraySet<Bitmap> dSet = new ArraySet<>(3);

        // add default icon
        {
            Drawable drawable = iconsHandler.getDrawableIconForPackage(cn, userHandle);

            checkDuplicateDrawable(dSet, drawable);

            ImageView icon = quickList.findViewById(android.R.id.icon);
            icon.setImageDrawable(drawable);
            icon.setOnClickListener(v -> {
                mSelectedDrawable = null;
                mPreview.setImageDrawable(((ImageView) v).getDrawable());
            });
            ((TextView) quickList.findViewById(android.R.id.text1)).setText(R.string.default_icon);
        }

        IconPack iconPack = mShownIconPack != null ? mShownIconPack : iconsHandler.getCustomIconPack();
        //IIconPack systemIconPack = iconsHandler.getSystemIconPack();

        // add getActivityIcon(componentName)
        {
            Drawable drawable = null;
            try {
                drawable = context.getPackageManager().getActivityIcon(cn);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            if (drawable != null) {
                if (checkDuplicateDrawable(dSet, drawable)) {
                    addQuickOption(R.string.custom_icon_activity, drawable, quickList);
                    if (iconPack != null)
                        addQuickOption(R.string.custom_icon_activity_with_pack, iconPack.applyBackgroundAndMask(context, drawable, true), quickList);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        addQuickOption(R.string.custom_icon_activity_adaptive, DrawableUtils.applyIconMaskShape(context, drawable, true), quickList);
                        addQuickOption(R.string.custom_icon_activity_adaptive_fill, DrawableUtils.applyIconMaskShape(context, drawable, false), quickList);
                    }
                }
            }
        }

        // add getApplicationIcon(packageName)
        {
            Drawable drawable = null;
            try {
                drawable = context.getPackageManager().getApplicationIcon(cn.getPackageName());
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            if (drawable != null) {
                if (checkDuplicateDrawable(dSet, drawable)) {
                    addQuickOption(R.string.custom_icon_application, drawable, quickList);
                    if (iconPack != null)
                        addQuickOption(R.string.custom_icon_application_with_pack, iconPack.applyBackgroundAndMask(context, drawable, true), quickList);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        addQuickOption(R.string.custom_icon_application_adaptive, DrawableUtils.applyIconMaskShape(context, drawable, true), quickList);
                        addQuickOption(R.string.custom_icon_application_adaptive_fill, DrawableUtils.applyIconMaskShape(context, drawable, false), quickList);
                    }
                }
            }
        }

        // add Activity BadgedIcon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            LauncherApps launcher = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
            assert launcher != null;
            List<LauncherActivityInfo> icons = launcher.getActivityList(cn.getPackageName(), userHandle.getRealHandle());
            for (LauncherActivityInfo info : icons) {
                Drawable drawable = info.getBadgedIcon(0);

                if (drawable != null) {
                    if (checkDuplicateDrawable(dSet, drawable)) {
                        addQuickOption(R.string.custom_icon_badged, drawable, quickList);
                        if (iconPack != null)
                            addQuickOption(R.string.custom_icon_badged_with_pack, iconPack.applyBackgroundAndMask(context, drawable, true), quickList);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            addQuickOption(R.string.custom_icon_badged_adaptive, DrawableUtils.applyIconMaskShape(context, drawable, true), quickList);
                            addQuickOption(R.string.custom_icon_badged_adaptive_fill, DrawableUtils.applyIconMaskShape(context, drawable, false), quickList);
                        }
                    }
                }
            }
        }
    }

    private boolean checkDuplicateDrawable(ArraySet<Bitmap> set, Drawable drawable) {
        Bitmap b = null;
        if (drawable instanceof BitmapDrawable)
            b = ((BitmapDrawable) drawable).getBitmap();

        if (set.contains(b))
            return false;

        set.add(b);
        return true;
    }


    private void addQuickOption(@StringRes int textId, Drawable drawable, ViewGroup parent) {
        if (!(drawable instanceof BitmapDrawable))
            return;

        ViewGroup layout = (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_icon_quick, parent, false);
        ImageView icon = layout.findViewById(android.R.id.icon);
        TextView text = layout.findViewById(android.R.id.text1);

        icon.setImageDrawable(drawable);
        icon.setOnClickListener(v -> {
            mSelectedDrawable = ((ImageView) v).getDrawable();
            mPreview.setImageDrawable(mSelectedDrawable);
        });

        text.setText(textId);

        parent.addView(layout);
    }

    private void setShownIconPack(String packageName) {
        if (packageName == null) {
            mShownIconPack = null;
        } else {
            if (mShownIconPack != null && packageName.equals(mShownIconPack.getPackPackageName()))
                return;
            IconPackXML pack = new IconPackXML(packageName);
            pack.loadDrawables(getContext().getPackageManager());
            mShownIconPack = pack;
        }
        refreshList();
        refreshQuickList();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        IconsHandler iconsHandler = TBApplication.getApplication(getActivity()).getIconsHandler();
        IconPack iconPack = iconsHandler.getCustomIconPack();
        String packName = iconPack != null ? iconPack.getPackPackageName() : null;
        if (packName == null && mIconPackList.getChildCount() > 0)
            packName = mIconPackList.getChildAt(0).getTag().toString();
        setShownIconPack(packName);
    }

    private void refreshList() {
        mIconData.clear();
        IconPackXML iconPack = mShownIconPack;
        if (iconPack != null) {
            Collection<IconPackXML.DrawableInfo> drawables = iconPack.getDrawableList();
            if (drawables != null) {
                StringNormalizer.Result normalized = StringNormalizer.normalizeWithResult(mSearch.getText(), true);
                FuzzyScore fuzzyScore = new FuzzyScore(normalized.codePoints);
                for (IconPackXML.DrawableInfo info : drawables) {
                    if (fuzzyScore.match(info.getDrawableName()).match)
                        mIconData.add(new IconData(iconPack, info));
                }
            }
        }
        boolean showGridAndSearch = !mIconData.isEmpty() || (mSearch.length() > 0);
        mSearch.setVisibility(showGridAndSearch ? View.VISIBLE : View.GONE);
        mIconGrid.setVisibility(showGridAndSearch ? View.VISIBLE : View.GONE);
        ((BaseAdapter) mIconGrid.getAdapter()).notifyDataSetChanged();
    }

    static class IconData {
        final IconPackXML.DrawableInfo drawableInfo;
        final IconPackXML iconPack;

        IconData(IconPackXML iconPack, IconPackXML.DrawableInfo drawableInfo) {
            this.iconPack = iconPack;
            this.drawableInfo = drawableInfo;
        }

        Drawable getIcon() {
            return iconPack.getDrawable(drawableInfo);
        }
    }

    static class IconAdapter extends BaseAdapter {
        private final List<IconData> mIcons;
        private OnItemClickListener mOnItemClickListener = null;

        public interface OnItemClickListener {
            void onItemClick(IconAdapter adapter, View view, int position);
        }

        IconAdapter(@NonNull List<IconData> objects) {
            mIcons = objects;
        }

        void setOnItemClickListener(OnItemClickListener listener) {
            mOnItemClickListener = listener;
        }

        @Override
        public IconData getItem(int position) {
            return mIcons.get(position);
        }

        @Override
        public int getCount() {
            return mIcons.size();
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).hashCode();
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            final View view;
            if (convertView == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.custom_icon_item, parent, false);
            } else {
                view = convertView;
            }
            ViewHolder holder = view.getTag() instanceof ViewHolder ? (ViewHolder) view.getTag() : new ViewHolder(view);

            IconData content = getItem(position);
            holder.setContent(content);

            holder.icon.setOnClickListener(v -> {
                if (mOnItemClickListener != null)
                    mOnItemClickListener.onItemClick(IconAdapter.this, v, position);
            });
            holder.icon.setOnLongClickListener(v -> {
                displayToast(v, content.drawableInfo.getDrawableName());
                return true;
            });

            return view;
        }

        /**
         * @param v       is the Button view that you want the Toast to appear above
         * @param message is the string for the message
         */

        private void displayToast(View v, CharSequence message) {
            int xOffset = 0;
            int yOffset = 0;
            Rect gvr = new Rect();

            View parent = (View) v.getParent();
            int parentHeight = parent.getHeight();

            if (v.getGlobalVisibleRect(gvr)) {
                View root = v.getRootView();

                int halfWidth = root.getRight() / 2;
                int halfHeight = root.getBottom() / 2;

                int parentCenterX = (gvr.width() / 2) + gvr.left;

                int parentCenterY = (gvr.height() / 2) + gvr.top;

                if (parentCenterY <= halfHeight) {
                    yOffset = -(halfHeight - parentCenterY);
                } else {
                    yOffset = (parentCenterY - halfHeight);
                }

                if (parentCenterX < halfWidth) {
                    xOffset = -(halfWidth - parentCenterX);
                }

                if (parentCenterX >= halfWidth) {
                    xOffset = parentCenterX - halfWidth;
                }
            }

            Toast toast = Toast.makeText(v.getContext(), message, Toast.LENGTH_SHORT);
            toast.setGravity(Gravity.CENTER, xOffset, yOffset + v.getHeight());
            toast.show();
        }

        static class ViewHolder {
            ImageView icon;
            AsyncLoad loader = null;

            static class AsyncLoad extends AsyncTask<IconData, Void, Drawable> {
                WeakReference<ViewHolder> holder;

                AsyncLoad(ViewHolder holder) {
                    this.holder = new WeakReference<>(holder);
                }

                @Override
                protected void onPreExecute() {
                    ViewHolder h = holder.get();
                    if (h == null || h.loader != this)
                        return;
                    h.icon.setImageDrawable(null);
                }

                @Override
                protected Drawable doInBackground(IconData... iconData) {
                    return iconData[0].getIcon();
                }

                @Override
                protected void onPostExecute(Drawable drawable) {
                    ViewHolder h = holder.get();
                    if (h == null || h.loader != this)
                        return;
                    h.loader = null;
                    h.icon.setImageDrawable(drawable);
                    h.icon.setScaleX(0f);
                    h.icon.setScaleY(0f);
                    h.icon.setRotation((drawable.hashCode() & 1) == 1 ? 180f : -180f);
                    h.icon.animate().scaleX(1f).scaleY(1f).rotation(0f).start();
                }
            }

            ViewHolder(View itemView) {
                itemView.setTag(this);
                icon = itemView.findViewById(android.R.id.icon);
            }

            public void setContent(IconData content) {
                if (loader != null)
                    loader.cancel(true);
                loader = new AsyncLoad(this);
                loader.execute(content);
            }
        }
    }
}
