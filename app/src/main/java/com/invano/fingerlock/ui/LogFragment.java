package com.invano.fingerlock.ui;

import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.invano.fingerlock.R;
import com.invano.fingerlock.util.LogFile;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;

public class LogFragment extends Fragment {

    private TextView logTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.log_fragment, container, false);
        logTextView = (TextView) rootView.findViewById(R.id.logTextView);
        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        new LogLoadAsyncTask(getActivity()).execute();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        getActivity().getMenuInflater().inflate(R.menu.menu_log, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();
        switch (id) {
            case R.id.action_clear_log:
                LogFile.delete(getActivity());
                logTextView.setText("");
                Toast.makeText(getActivity(), R.string.log_deleted, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    private class LogLoadAsyncTask extends AsyncTask<Void, Void, String> {

        private Context context;

        public LogLoadAsyncTask(Activity context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... params) {
            String logString = null;
            try {
                if (context != null) {
                    FileInputStream fin = context.openFileInput("log.txt");
                    logString = convertStreamToString(fin);
                    fin.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return logString;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (s != null)
                logTextView.setText(s);
        }
    }
}
