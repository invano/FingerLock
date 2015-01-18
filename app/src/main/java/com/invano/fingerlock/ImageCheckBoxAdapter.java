package com.invano.fingerlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImageCheckBoxAdapter extends RecyclerView.Adapter<ImageCheckBoxAdapter.ViewHolder> {


    private List<Map<String, Object>> mItemList, oriItemList;
    private SharedPreferences pref;
    private Filter mFilter;
    private OnPackageSelectedListener listener;

    public ImageCheckBoxAdapter(Context context, List<Map<String, Object>> itemList) {
        oriItemList = mItemList = itemList;
        pref = context.getSharedPreferences(this.getClass().getPackage().getName(), Context.MODE_WORLD_READABLE);
        mFilter = new MyFilter();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View itemLayoutView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.image_checkbox_adapter, parent, false);

        return new ViewHolder(itemLayoutView);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        final String title = (String) mItemList.get(position).get("title");
        if (title.length() >= 25) {
            holder.title.setText(title.substring(0, 24) + "…");
        } else
            holder.title.setText(title);

        final String key = (String) mItemList.get(position).get("key");
        if (key.length() >= 35) {
            holder.pkg.setText(key.substring(0, 34) + "…");
        } else
            holder.pkg.setText(key);

        holder.icon.setTag(key);

        new IconLoader(holder.icon, key).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        if(pref.getBoolean(key, false)) {
            holder.checkBox.setChecked(true);
        }
        else {
            holder.checkBox.setChecked(false);
        }

        holder.checkBox.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                CheckBox cb = (CheckBox) v;

                boolean value = cb.isChecked();
                pref.edit().putBoolean(key, value).apply();
                if (listener != null) {
                    listener.onPackageChanged(key);
                }
            }
        });
    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        super.onViewRecycled(holder);

        holder.icon.setImageDrawable(null);
    }

    @Override
    public long getItemId(int position) {
        return mItemList.get(position).hashCode();
    }

    @Override
    public int getItemCount() {
        return mItemList.size();
    }

    public Filter getFilter() {
        return mFilter;
    }

    class MyFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {

            constraint = constraint.toString().toLowerCase();

            FilterResults results = new FilterResults();

            if(constraint.length() == 0) {
                results.values = oriItemList;
                results.count = oriItemList.size();
            }
            else {
                List<Map<String, Object>> filteredList = new ArrayList<>();

                for(Map<String, Object> app : oriItemList) {
                    String title = ((String) app.get("title")).toLowerCase();
                    if(title.indexOf((String) constraint) == 0) {
                        filteredList.add(app);
                    }
                }

                results.values = filteredList;
                results.count = filteredList.size();
            }
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {

            mItemList = (List<Map<String, Object>>) results.values;
            notifyDataSetChanged();
        }

    }

    public static class ViewHolder extends RecyclerView.ViewHolder
    {
        TextView title;
        TextView pkg;
        ImageView icon;
        CheckBox checkBox;

        public ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.titleTextView);
            pkg = (TextView) itemView.findViewById(R.id.packageTextView);
            icon = (ImageView) itemView.findViewById(R.id.iconImageView);
            checkBox = (CheckBox) itemView.findViewById(R.id.checkbox);
        }
    }

    private class IconLoader extends AsyncTask<Void, Void, Object> {
        private ImageView iconView;
        private String packageName;

        public IconLoader(ImageView view, String packageName) {
            this.packageName = packageName;
            iconView = view;
        }

        @Override
        protected Object doInBackground(Void... params) {
            Bitmap iconBitmap = null;
            Drawable iconDrawable = null;

            try {
                Bitmap icon = ((BitmapDrawable) iconView.getContext().getPackageManager().getApplicationIcon(packageName)).getBitmap();
                if (icon.getHeight() > 192 || icon.getWidth() > 192) {
                    iconBitmap = Bitmap.createScaledBitmap(icon, 192, 192, true);
                } else {
                    iconBitmap = icon;
                }
            } catch (ClassCastException e) {
                try {
                    iconDrawable = iconView.getContext().getPackageManager().getApplicationIcon(packageName);
                } catch (PackageManager.NameNotFoundException e1) {
                    e1.printStackTrace();
                }
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }

            if (iconBitmap != null)
                return iconBitmap;
            else
                return iconDrawable;
        }

        @Override
        protected void onPostExecute(Object result) {
            super.onPostExecute(result);
            if (iconView.getTag().toString().equals(packageName)) {
                if (result instanceof Bitmap)
                    iconView.setImageBitmap((Bitmap) result);
                else
                    iconView.setImageDrawable((Drawable) result);
            }
        }
    }

    public void registerOnPackageSelected (OnPackageSelectedListener listener) {
        this.listener = listener;
    }
}
