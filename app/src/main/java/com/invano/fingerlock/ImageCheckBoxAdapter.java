package com.invano.fingerlock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ImageCheckBoxAdapter extends BaseAdapter {


    private List<Map<String, Object>> mItemList, oriItemList;
    private LayoutInflater mInflater;
    private SharedPreferences pref;
    private Filter mFilter;
    private OnPackageSelectedListener listener;

    public ImageCheckBoxAdapter(Context context, List<Map<String, Object>> itemList) {
        mInflater = LayoutInflater.from(context);
        oriItemList = mItemList = itemList;
        pref = context.getSharedPreferences(this.getClass().getPackage().getName(), Context.MODE_WORLD_READABLE);
        mFilter = new MyFilter();
    }

    @Override
    public int getCount() {
        return mItemList.size();
    }

    @Override
    public Object getItem(int position) {
        return mItemList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mItemList.get(position).hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder vh;

        if(convertView == null) {
            vh = new ViewHolder();
            convertView = mInflater.inflate(R.layout.image_checkbox_adapter, parent, false);
            vh.title = (TextView) convertView.findViewById(R.id.title);
            vh.icon = (ImageView) convertView.findViewById(R.id.icon);
            vh.checkBox = (CheckBox) convertView.findViewById(R.id.checkbox);
            convertView.setTag(vh);
        }
        else {
            vh = (ViewHolder) convertView.getTag();
        }

        final String itemTitle = (String) mItemList.get(position).get("title");
        final String key = (String) mItemList.get(position).get("key");
        final Drawable itemIcon = (Drawable) mItemList.get(position).get("icon");

        vh.title.setText(itemTitle);
        vh.icon.setImageDrawable(itemIcon);

        if(pref.getBoolean(key, false)) {
            vh.checkBox.setChecked(true);
        }
        else {
            vh.checkBox.setChecked(false);
        }

        vh.checkBox.setOnClickListener(new View.OnClickListener() {

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

        return convertView;
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
                List<Map<String, Object>> filteredList = new ArrayList<Map<String, Object>>();

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

    static class ViewHolder
    {
        TextView title;
        ImageView icon;
        CheckBox checkBox;
    }

    public void registerOnPackageSelected (OnPackageSelectedListener listener) {
        this.listener = listener;
    }
}
