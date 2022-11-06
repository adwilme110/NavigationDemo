package com.example.android.navigationsample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.FloatingWindow;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigator;
import androidx.navigation.NavigatorProvider;
import androidx.navigation.fragment.FragmentNavigator;

import java.util.HashSet;

@Navigator.Name("view")
public class YDViewFragmentNavigator extends Navigator<YDViewFragmentNavigator.Destination> {

    private static final String TAG = "YDViewNavigator";
    private static final String KEY_DIALOG_COUNT = "androidx-nav-viewfragment:navigator:count";
    private static final String DIALOG_TAG = "androidx-nav-fragment:navigator:view:";

    private final Context mContext;
    private final FragmentManager mFragmentManager;
    private int mDialogCount = 0;
    private final HashSet<String> mRestoredTagsAwaitingAttach = new HashSet<>();

    private LifecycleEventObserver mObserver = new LifecycleEventObserver() {
        @Override
        public void onStateChanged(@NonNull LifecycleOwner source,
                                   @NonNull Lifecycle.Event event) {
            if (event == Lifecycle.Event.ON_STOP) {
                Fragment fm = (Fragment) source;
//                if (!dialogFragment.requireDialog().isShowing()) {
//                    NavHostFragment.findNavController(dialogFragment).popBackStack();
//                }
            }
        }
    };

    public YDViewFragmentNavigator(@NonNull Context context, @NonNull FragmentManager manager) {
        mContext = context;
        mFragmentManager = manager;
    }

    @SuppressLint("UsingALog")
    @Override
    public boolean popBackStack() {
        if (mDialogCount == 0) {
            return false;
        }
        if (mFragmentManager.isStateSaved()) {
            Log.i(TAG, "Ignoring popBackStack() call: FragmentManager has already"
                    + " saved its state");
            return false;
        }
        Fragment existingFragment = mFragmentManager
                .findFragmentByTag(DIALOG_TAG + --mDialogCount);
        if (existingFragment != null) {
            existingFragment.getLifecycle().removeObserver(mObserver);
            FragmentTransaction ft = existingFragment.getParentFragmentManager().beginTransaction();
            ft.remove(existingFragment);
            ft.commit();
        }
        return true;
    }

    @NonNull
    @Override
    public Destination createDestination() {
        return new Destination(this);
    }

    @SuppressLint("UsingALog")
    @Nullable
    @Override
    public NavDestination navigate(@NonNull final Destination destination, @Nullable Bundle args,
                                   @Nullable NavOptions navOptions, @Nullable Extras navigatorExtras) {
        if (mFragmentManager.isStateSaved()) {
            Log.i(TAG, "Ignoring navigate() call: FragmentManager has already"
                    + " saved its state");
            return null;
        }
        String className = destination.getClassName();
        if (className.charAt(0) == '.') {
            className = mContext.getPackageName() + className;
        }
        final Fragment frag = mFragmentManager.getFragmentFactory().instantiate(
                mContext.getClassLoader(), className);
        if (!Fragment.class.isAssignableFrom(frag.getClass())) {
            throw new IllegalArgumentException("YDViewNavigator destination " + destination.getClassName()
                    + " is not an instance of Fragment");
        }
        final Fragment dialogFragment = (Fragment) frag;
        dialogFragment.setArguments(args);
        dialogFragment.getLifecycle().addObserver(mObserver);
        FragmentTransaction ft = mFragmentManager.beginTransaction();
        ft.add(dialogFragment, DIALOG_TAG + mDialogCount++);
        ft.commit();
        return destination;
    }

    @Override
    @Nullable
    public Bundle onSaveState() {
        if (mDialogCount == 0) {
            return null;
        }
        Bundle b = new Bundle();
        b.putInt(KEY_DIALOG_COUNT, mDialogCount);
        return b;
    }

    @Override
    public void onRestoreState(@Nullable Bundle savedState) {
        if (savedState != null) {
            mDialogCount = savedState.getInt(KEY_DIALOG_COUNT, 0);
            for (int index = 0; index < mDialogCount; index++) {
                Fragment fragment = (Fragment) mFragmentManager
                        .findFragmentByTag(DIALOG_TAG + index);
                if (fragment != null) {
                    fragment.getLifecycle().addObserver(mObserver);
                } else {
                    mRestoredTagsAwaitingAttach.add(DIALOG_TAG + index);
                }
            }
        }
    }

    // TODO: Switch to FragmentOnAttachListener once we depend on Fragment 1.3
    void onAttachFragment(@NonNull Fragment childFragment) {
        boolean needToAddObserver = mRestoredTagsAwaitingAttach.remove(childFragment.getTag());
        if (needToAddObserver) {
            childFragment.getLifecycle().addObserver(mObserver);
        }
    }

    @NavDestination.ClassType(Fragment.class)
    public static class Destination extends NavDestination implements FloatingWindow {

        private String mClassName;

        /**
         * Construct a new fragment destination. This destination is not valid until you set the
         * Fragment via {@link #setClassName(String)}.
         *
         * @param navigatorProvider The {@link NavController} which this destination
         *                          will be associated with.
         */
        public Destination(@NonNull NavigatorProvider navigatorProvider) {
            this(navigatorProvider.getNavigator(YDViewFragmentNavigator.class));
        }

        /**
         * Construct a new fragment destination. This destination is not valid until you set the
         * Fragment via {@link #setClassName(String)}.
         *
         * @param fragmentNavigator The {@link FragmentNavigator} which this destination
         *                          will be associated with. Generally retrieved via a
         *                          {@link NavController}'s
         *                          {@link NavigatorProvider#getNavigator(Class)} method.
         */
        public Destination(@NonNull Navigator<? extends Destination> fragmentNavigator) {
            super(fragmentNavigator);
        }

        @CallSuper
        @Override
        public void onInflate(@NonNull Context context, @NonNull AttributeSet attrs) {
            super.onInflate(context, attrs);
            TypedArray a = context.getResources().obtainAttributes(attrs,
                    R.styleable.FragmentNavigator);
            String className = a.getString(R.styleable.FragmentNavigator_android_name);
            if (className != null) {
                setClassName(className);
            }
            a.recycle();
        }

        /**
         * Set the Fragment class name associated with this destination
         *
         * @param className The class name of the Fragment to show when you navigate to this
         *                  destination
         * @return this {@link FragmentNavigator.Destination}
         */
        @NonNull
        public final Destination setClassName(@NonNull String className) {
            mClassName = className;
            return this;
        }

        /**
         * Gets the Fragment's class name associated with this destination
         *
         * @throws IllegalStateException when no Fragment class was set.
         */
        @NonNull
        public final String getClassName() {
            if (mClassName == null) {
                throw new IllegalStateException("Fragment class was not set");
            }
            return mClassName;
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.toString());
            sb.append(" class=");
            if (mClassName == null) {
                sb.append("null");
            } else {
                sb.append(mClassName);
            }
            return sb.toString();
        }
    }
}
