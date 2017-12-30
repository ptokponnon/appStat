package com.example.parfait.appstat;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = AppUsageStatisticsFragment.class.getSimpleName();

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link android.support.v4.app.FragmentStatePagerAdapter}.
     */
    //VisibleForTesting for variables below
    static int numPage = 5;
    static UsageStatsManager mUsageStatsManager;
    static List<UsageListAdapter> mUsageListAdapters = new ArrayList<>();
    static Button mOpenUsageSettingButton;
    RecyclerView.LayoutManager mLayoutManager;
    Spinner mSpinner;
    static StatsUsageInterval statsUsageInterval;
    static List<List<UsageStats>> usageStatsListList = new ArrayList<>();
    static List<String> totalTexts = new ArrayList<>();
    static List<String> afficheTexts = new ArrayList<>();

    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE); //Context.USAGE_STATS_SERVICE
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());
        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        mOpenUsageSettingButton = findViewById(R.id.button_open_usage_setting);
        mSpinner = findViewById(R.id.spinner_time_span);
        SpinnerAdapter spinnerAdapter = ArrayAdapter.createFromResource(this,
                R.array.action_list, android.R.layout.simple_spinner_dropdown_item);
        mSpinner.setAdapter(spinnerAdapter);
        mSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            String[] strings = getResources().getStringArray(R.array.action_list);

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                statsUsageInterval = StatsUsageInterval.getValue(strings[position]);
                if (statsUsageInterval != null) {
                    List<UsageStats> usageStatsList = getUsageStatistics(statsUsageInterval.mInterval);
                    if(usageStatsList.size()>0){
                        usageStatsListList = explodeList(usageStatsList);
                        numPage = usageStatsListList.size();
                        mSectionsPagerAdapter.notifyDataSetChanged();
                        for(int i=0; i<usageStatsListList.size(); i++){
                            Collections.sort(usageStatsListList.get(i), new TotalTimeUsedComparator());
                            updateAppsList(usageStatsListList.get(i), i);
                        }
                        mViewPager.setAdapter(mSectionsPagerAdapter);
                        mViewPager.setCurrentItem(usageStatsListList.size(), false);
                    }
                    TextView textView = findViewById(R.id.app_number);
                    textView.setText(getString(R.string.total, usageStatsList.size(), usageStatsListList.size()));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Returns the {@link #mUsageStatsManager} including the time span specified by the
     * intervalType argument.
     *
     * @param intervalType The time interval by which the stats are aggregated.
     *                     Corresponding to the value of {@link UsageStatsManager}.
     *                     E.g. {@link UsageStatsManager#INTERVAL_DAILY}, {@link
     *                     UsageStatsManager#INTERVAL_WEEKLY},
     *
     * @return A list of {@link android.app.usage.UsageStats}.
     */
    public List<UsageStats> getUsageStatistics(int intervalType) {
        // Get the app statistics since one year ago from the current time.
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, -5);

        List<UsageStats> queryUsageStats = mUsageStatsManager
                .queryUsageStats(intervalType, cal.getTimeInMillis(),
                        System.currentTimeMillis());
        if (queryUsageStats.size() == 0) {
            Log.i(TAG, "The user may not allow the access to apps usage. ");
            Toast.makeText(this,
                    getString(R.string.explanation_access_to_appusage_is_not_enabled),
                    Toast.LENGTH_LONG).show();
            mOpenUsageSettingButton.setVisibility(View.VISIBLE);
            mOpenUsageSettingButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                }
            });
        }
        List<UsageStats> to_remove = new ArrayList<>();
        for (int i = 0; i < queryUsageStats.size(); i++) {
//            String packageName = queryUsageStats.get(i).getPackageName();
//            try {
//                ApplicationInfo ai = getActivity().getPackageManager().getApplicationInfo(packageName, 0);
//                int mask = ApplicationInfo.FLAG_SYSTEM;
//                if((ai.flags & mask) != 0){
//                    to_remove.add(queryUsageStats.get(i));
//                    System.out.println("sys "+queryUsageStats.get(i).getPackageName()+ " "+Integer.toHexString(ai.flags));
//                    continue;
//                }
//            }catch (PackageManager.NameNotFoundException ex){
//                Log.w(TAG, String.format("NameNotFoundException %s", packageName));
//            }

            if(queryUsageStats.get(i).getTotalTimeInForeground() < 5000){
                to_remove.add(queryUsageStats.get(i));
            }

        }
        System.out.println("Total1 "+queryUsageStats.size());
        queryUsageStats.removeAll(to_remove);
        to_remove.clear();
        to_remove.addAll(queryUsageStats);
        System.out.println("Total2 "+queryUsageStats.size());
        return queryUsageStats;
    }

    public static int number_of(List<UsageStats> usageStatsList, UsageStats usl){
        int num = 0;
        for (int i = 0; i < usageStatsList.size(); i++) {
            if (usageStatsList.get(i).getPackageName().equals(usl.getPackageName()))
                num ++;
        }
        return num;
    }

    List<List<UsageStats>> explodeList(List<UsageStats> usageStatsList){
        List<List<UsageStats>> usageStatsListList = new ArrayList<List<UsageStats>>();
        List<UsageStats> usl = new ArrayList<>();
        long firstTimeStamp = usageStatsList.get(0).getFirstTimeStamp();
        for (int i=0; i<usageStatsList.size(); i++){
            if(firstTimeStamp == usageStatsList.get(i).getFirstTimeStamp()) {
                usl.add(usageStatsList.get(i));
            }else {
                usageStatsListList.add(usl);
                usl = new ArrayList<>();
                firstTimeStamp = usageStatsList.get(i).getFirstTimeStamp();
                usl.add(usageStatsList.get(i));
            }
        }
        usageStatsListList.add(usl);
        return usageStatsListList;
    }

    /**
     * Updates the {@link #mUsageListAdapters} with the list of {@link UsageStats} passed as an argument.
     *
     * @param usageStatsList A list of {@link UsageStats} from which update the
     *                       {@link #mUsageListAdapters}.
     */
    void updateAppsList(List<UsageStats> usageStatsList, int page) {
        List<CustomUsageStats> customUsageStatsList = new ArrayList<>();
        for (int i = 0; i < usageStatsList.size(); i++) {
            CustomUsageStats customUsageStats = new CustomUsageStats();
            customUsageStats.usageStats = usageStatsList.get(i);
            try {
                Drawable appIcon = getPackageManager()
                        .getApplicationIcon(customUsageStats.usageStats.getPackageName());
                customUsageStats.appIcon = appIcon;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, String.format("App Icon is not found for %s",
                        customUsageStats.usageStats.getPackageName()));
                customUsageStats.appIcon = getDrawable(R.drawable.ic_default_app_launcher);
            }
            customUsageStatsList.add(customUsageStats);
        }
        UsageListAdapter mUsageListAdapter = new UsageListAdapter();
        mUsageListAdapter.setCustomUsageStatsList(customUsageStatsList);
        mUsageListAdapter.notifyDataSetChanged();
        mUsageListAdapters.add(page, mUsageListAdapter);
        totalTexts.add(page, getString(R.string.section_format, page+1, usageStatsListList.size(), usageStatsList.size()));
        afficheTexts.add(page, affiche(page));
    }

    String affiche(int page){
        int last = usageStatsListList.size()-1;
        switch (statsUsageInterval){
            case DAILY:
                if (page == last) return "Aujourdhui";
                if (page == last-1) return "Hier";
                switch (page){
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        DateFormat mDateFormat = new SimpleDateFormat("dd/MM/yyy");
                        Calendar cal = Calendar.getInstance();
                        cal.add(Calendar.DAY_OF_YEAR, -last+page);
                        return mDateFormat.format(cal.getTime());
                    case 5: return "Aujourdhui";
                    case 6: return "Hier";

                }
            case WEEKLY:
                if(page == last) return "Cette semaine";
                if(page == last-1) return "La semaine passée";
                switch (page){
                    case 0:
                    case 1:
                    case 3:
                    case 4:
                        DateFormat mDateFormat = new SimpleDateFormat("dd/MM/yyy");
                        Calendar cal1 = Calendar.getInstance(),
                        cal2 = Calendar.getInstance();
                        cal1.add(Calendar.WEEK_OF_YEAR, -last+page);
                        cal2.add(Calendar.WEEK_OF_YEAR, -last+page+1);
                        return "De "+mDateFormat.format(cal1.getTime())+ " à "+ mDateFormat.format(cal2.getTime());

                }
            case MONTHLY:
                if(page == last) return "Ce mois";
                if(page == last-1) return "Le mois passé";
                switch (page){
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                        DateFormat mDateFormat = new SimpleDateFormat("dd/MM/yyy");
                        Calendar cal1 = Calendar.getInstance(),
                                cal2 = Calendar.getInstance();
                        cal1.add(Calendar.YEAR, -last+page);
                        cal2.add(Calendar.YEAR, -last+page+1);
                        return "De "+mDateFormat.format(cal1.getTime())+ " à "+ mDateFormat.format(cal2.getTime());
                }
            case YEARLY:
                if(page == last)return "Cette année";
                if(page == last-1) return "L'année dernière";
                switch (page){
                    case 0:
                    case 1:
                        DateFormat mDateFormat = new SimpleDateFormat("dd/MM/yyy");
                        Calendar cal1 = Calendar.getInstance(),
                                cal2 = Calendar.getInstance();
                        cal1.add(Calendar.YEAR, -last+page);
                        cal2.add(Calendar.YEAR, -last+page+1);
                        return "De "+mDateFormat.format(cal1.getTime())+ " à "+ mDateFormat.format(cal2.getTime());

                }
        }
        return "";
    }

    /**
     * The {@link Comparator} to sort a collection of {@link UsageStats} sorted by the timestamp
     * last time the app was used in the descendant order.
     */
    private static class TotalTimeUsedComparator implements Comparator<UsageStats> {

        @Override
        public int compare(UsageStats left, UsageStats right) {
            return Long.compare(right.getTotalTimeInForeground(), left.getTotalTimeInForeground());
        }
    }

    /**
     * Enum represents the intervals for {@link android.app.usage.UsageStatsManager} so that
     * values for intervals can be found by a String representation.
     *
     */
    //VisibleForTesting
    static enum StatsUsageInterval {
        DAILY("Jour", UsageStatsManager.INTERVAL_DAILY),
        WEEKLY("Semaine", UsageStatsManager.INTERVAL_WEEKLY),
        MONTHLY("Mois", UsageStatsManager.INTERVAL_MONTHLY),
        YEARLY("An", UsageStatsManager.INTERVAL_YEARLY);

        private int mInterval;
        private String mStringRepresentation;

        StatsUsageInterval(String stringRepresentation, int interval) {
            mStringRepresentation = stringRepresentation;
            mInterval = interval;
        }

        static StatsUsageInterval getValue(String stringRepresentation) {
            for (StatsUsageInterval statsUsageInterval : values()) {
                if (statsUsageInterval.mStringRepresentation.equals(stringRepresentation)) {
                    return statsUsageInterval;
                }
            }
            return null;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class AppUsageStatisticsFragment extends Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_SECTION_NUMBER = "section_number";

        public AppUsageStatisticsFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static AppUsageStatisticsFragment newInstance(int sectionNumber) {
            AppUsageStatisticsFragment fragment = new AppUsageStatisticsFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_SECTION_NUMBER, sectionNumber);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            TextView textView = rootView.findViewById(R.id.section_label);
            int page = getArguments().getInt(ARG_SECTION_NUMBER);
            textView.setText((page<totalTexts.size())? totalTexts.get(page): "Page: "+page+1);
            TextView textView1 = rootView.findViewById(R.id.affiche_label);
            textView1.setText(page<afficheTexts.size()?afficheTexts.get(page):"");
            if(page<mUsageListAdapters.size()){
                RecyclerView mRecyclerView = rootView.findViewById(R.id.recyclerview_app_usage);
                RecyclerView.LayoutManager mLayoutManager = mRecyclerView.getLayoutManager();
                mRecyclerView.scrollToPosition(0);
                mRecyclerView.setAdapter(mUsageListAdapters.get(page));
            }
            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            // getItem is called to instantiate the fragment for the given page.
            // Return a PlaceholderFragment (defined as a static inner class below).
            return AppUsageStatisticsFragment.newInstance(position);
        }

        @Override
        public int getCount() {
            // Show 3 total pages.
            return numPage;
        }
    }
}
