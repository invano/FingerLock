package com.invano.fingerlock.ui;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.invano.fingerlock.FLApplication;
import com.invano.fingerlock.OnPackageSelectedListener;
import com.invano.fingerlock.util.Util;
import com.invano.fingerlock.ImageCheckBoxAdapter;
import com.invano.fingerlock.R;
import com.stericson.RootTools.RootTools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class PackageListFragment extends Fragment implements SearchView.OnQueryTextListener {

    private RecyclerView packagesListView;
    private ImageCheckBoxAdapter listAdapter;
    private SearchView searchView;

    private ProgressBar progressBar;

    private HashSet<String> changedPackages;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
        changedPackages = new HashSet<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.package_selector_main, container, false);
        packagesListView = (RecyclerView)rootView.findViewById(R.id.listViewPkg);
        progressBar = (ProgressBar)rootView.findViewById(R.id.progressBarApps);

        packagesListView.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(getActivity());
        packagesListView.setLayoutManager(llm);

        return rootView;

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (listAdapter == null) {
            new AppListTask(getActivity()).execute();
        }
    }

    @Override
    public void onPause() {
        restartAppsHint(getActivity(), changedPackages);
        super.onPause();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.menu_package_selector, menu);
        searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        setupSearchView();
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        switch (id) {
            case R.id.action_search:
                item.expandActionView();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        listAdapter.getFilter().filter(s);
        return true;
    }

    private void setupSearchView() {
        searchView.setIconifiedByDefault(true);
        searchView.setOnQueryTextListener(this);
        searchView.setQueryHint(getResources().getString(R.string.search_hint));
    }

    private class AppListTask extends AsyncTask<Void, Integer, List<Map<String, Object>>> {

        Context context;

        public AppListTask(Activity context) {
            this.context = context;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<Map<String, Object>> doInBackground(Void... voids) {

            HashSet<String> uniqueSet = new HashSet<>();

            PackageManager pm = context.getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN, null);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);

            ArrayList<Map<String, Object>> items = new ArrayList<>();
            progressBar.setMax(list.size());
            int nApps = 1;

            for(ResolveInfo info : list) {
                if((FLApplication.showSystemApps()
                        || (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                        && !Util.MY_PACKAGE_NAME.equals(info.activityInfo.packageName)) {

                    if (uniqueSet.contains(info.activityInfo.packageName)) {
                        publishProgress(nApps++);
                        continue;
                    }

                    Map<String, Object> map = new HashMap<>();
                    String label = pm.getApplicationLabel(info.activityInfo.applicationInfo).toString();
                    Bitmap iconBitmap = null;
                    Drawable iconDrawable = null;

                    try {
                        Bitmap icon = ((BitmapDrawable) pm.getApplicationIcon(info.activityInfo.packageName)).getBitmap();
                        if (icon.getHeight() > 192 || icon.getWidth() > 192) {
                            iconBitmap = Bitmap.createScaledBitmap(icon, 192, 192, true);
                        } else {
                            iconBitmap = icon;
                        }
                    } catch (ClassCastException e) {
                        try {
                            iconDrawable = pm.getApplicationIcon(info.activityInfo.packageName);
                        } catch (PackageManager.NameNotFoundException e1) {
                            e1.printStackTrace();
                        }
                    } catch (PackageManager.NameNotFoundException e) {
                        e.printStackTrace();
                    }

                    map.put("title", label);
                    map.put("key", info.activityInfo.packageName);

                    if (iconBitmap != null)
                        map.put("icon", iconBitmap);
                    else
                        map.put("icon", iconDrawable);

                    items.add(map);
                    uniqueSet.add(info.activityInfo.packageName);
                    publishProgress(nApps++);
                }
            }

            Collections.sort(items, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2) {
                    String s1 = (String) o1.get("title");
                    String s2 = (String) o2.get("title");
                    return s1.compareToIgnoreCase(s2);
                }
            });

            return items;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            progressBar.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(List<Map<String, Object>> items) {
            super.onPostExecute(items);
            listAdapter = new ImageCheckBoxAdapter(context, items);
            listAdapter.registerOnPackageSelected(new OnPackageSelectedListener() {

                @Override
                public void onPackageChanged(String val) {
                    if (changedPackages.contains(val)) {
                        changedPackages.remove(val);
                    }
                    else {
                        changedPackages.add(val);
                    }
                }
            });

            packagesListView.setAdapter(listAdapter);
            packagesListView.setItemAnimator(new DefaultItemAnimator());
            progressBar.setVisibility(View.INVISIBLE);
        }
    }

    private static int findPIDbyPackageName(Context c, String packagename) {
        int result = -1;
        final ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            for (ActivityManager.RunningAppProcessInfo pi : am.getRunningAppProcesses()){
                if (pi.processName.equalsIgnoreCase(packagename)) {
                    result = pi.pid;
                }
                if (result != -1)
                    break;
            }
        } else {
            result = -1;
        }
        return result;
    }

    private void restartAppsHint(Context c, Set<String> set) {
        if (set.size() == 0)
            return;

        if (!RootTools.isAccessGiven()) {
            Toast.makeText(c, R.string.modified_apps_manual, Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(c, R.string.modified_apps_auto, Toast.LENGTH_SHORT).show();
            for (String pkg : set) {
                int pid = findPIDbyPackageName(c, pkg);
                Util.execute("kill " + Integer.toString(pid));
            }
            Toast.makeText(c, R.string.done, Toast.LENGTH_SHORT).show();
        }
    }
}
