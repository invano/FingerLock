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
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
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

    static private ListView packagesListView;
    private ImageCheckBoxAdapter listAdapter;
    private SearchView searchView;

    private ProgressDialog progressDialog;

    private HashSet<String> changedPackages;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.package_selector_main, container, false);
        packagesListView = (ListView)rootView.findViewById(R.id.listViewPkg);
        changedPackages = new HashSet<>();
        return rootView;

    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter == null || listAdapter.isEmpty()) {
            new AppListTask().execute();
        }
    }

    @Override
    public void onPause() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
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

        final AsyncTask task = this;
        Context context = getActivity();

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog = new ProgressDialog(context);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(true);
            progressDialog.show();
            progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialogInterface) {
                    task.cancel(true);
                }
            });
        }

        @Override
        protected List<Map<String, Object>> doInBackground(Void... voids) {

            PackageManager pm = context.getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN, null);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> list = pm.queryIntentActivities(i, 0);

            ArrayList<Map<String, Object>> items = new ArrayList<>();
            progressDialog.setMax(list.size());
            int nApps = 0;
            for(ResolveInfo info : list) {
                if((FLApplication.showSystemApps()
                        || (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0)
                        && !Util.MY_PACKAGE_NAME.equals(info.activityInfo.packageName)) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("title", info.loadLabel(pm).toString());
                    map.put("key", info.activityInfo.packageName);
                    map.put("icon", info.loadIcon(pm));
                    items.add(map);
                    nApps++;
                    publishProgress(nApps);
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
            progressDialog.setProgress(progress[0]);
        }

        @Override
        protected void onPostExecute(List<Map<String, Object>> items) {
            super.onPostExecute(items);

            if (progressDialog != null && progressDialog.isShowing()) {
                if (!((Activity) context).isFinishing() && !((Activity) context).isDestroyed()) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            }
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
