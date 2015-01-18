package com.invano.fingerlock.ui;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.res.TypedArray;
import android.support.v13.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import com.invano.fingerlock.R;

import it.neokree.materialtabs.MaterialTab;
import it.neokree.materialtabs.MaterialTabHost;
import it.neokree.materialtabs.MaterialTabListener;


public class MainWrapperActivity extends ActionBarActivity implements MaterialTabListener {

    private MaterialTabHost tabHost;
    private ViewPager pager;
    private ViewPagerAdapter adapter;

    private boolean locked = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_wrapper_activity);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        pager = (ViewPager) this.findViewById(R.id.pager );
        adapter = new ViewPagerAdapter(getFragmentManager());

        tabHost = (MaterialTabHost) this.findViewById(R.id.tabHost);
        // insert all tabs from pagerAdapter data
        for (int i = 0; i < adapter.getCount(); i++) {
            tabHost.addTab(
                    tabHost.newTab()
                            .setText(adapter.getPageTitle(i))
                            .setTabListener(this)
            );

        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        TypedArray array = getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground,
        });
        int backgroundColor = array.getColor(0, 0xFF00FF);
        array.recycle();

        if(!locked) {
            locked = true;
            Intent i = new Intent(this, LockActivity.class);
            i.putExtra("BACKGROUND", backgroundColor);
            startActivityForResult(i, 1);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {

            locked = false;

            if(resultCode == RESULT_OK && pager.getAdapter() == null){
                // init view pager
                pager.setAdapter(adapter);
                pager.setOffscreenPageLimit(2);
                pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        // when user do a swipe the selected tab change
                        tabHost.setSelectedNavigationItem(position);

                    }
                });

            }
            if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
    }

    @Override
    public void onTabSelected(MaterialTab tab) {
        pager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabReselected(MaterialTab tab) {

    }

    @Override
    public void onTabUnselected(MaterialTab tab) {

    }

    private class ViewPagerAdapter extends FragmentStatePagerAdapter {

        public ViewPagerAdapter(FragmentManager fm) {
            super(fm);

        }

        public Fragment getItem(int num) {
            switch (num) {
                case 0:
                    return new PackageListFragment();
                case 1:
                    return new LogFragment();
                case 2:
                    return new SettingsFragment();
            }

            return null;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.packages);
                case 1:
                    return getString(R.string.log);
                case 2:
                    return getString(R.string.settings);
            }
            return "";
        }

    }

}
