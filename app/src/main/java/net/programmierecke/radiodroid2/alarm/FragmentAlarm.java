package net.programmierecke.radiodroid2.alarm;

import android.app.TimePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import net.programmierecke.radiodroid2.R;
import net.programmierecke.radiodroid2.RadioDroidApp;

import java.util.Observer;

public class FragmentAlarm extends Fragment implements TimePickerDialog.OnTimeSetListener {
    private RadioAlarmManager ram;
    private ItemAdapterRadioAlarm adapterRadioAlarm;
    private ListView lvAlarms;
    private Observer alarmsObserver;

    public FragmentAlarm() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RadioDroidApp radioDroidApp = (RadioDroidApp)getActivity().getApplication();
        ram = radioDroidApp.getAlarmManager();

        View view = inflater.inflate(R.layout.layout_alarms, container, false);

        adapterRadioAlarm = new ItemAdapterRadioAlarm(getActivity());
        lvAlarms = view.findViewById(R.id.listViewAlarms);
        lvAlarms.setAdapter(adapterRadioAlarm);
        lvAlarms.setClickable(true);
        lvAlarms.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Object anObject = parent.getItemAtPosition(position);
                if (anObject instanceof DataRadioStationAlarm) {
                    ClickOnItem((DataRadioStationAlarm) anObject);
                }
            }
        });
        lvAlarms.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Object alarm = parent.getItemAtPosition(position);
                if (alarm instanceof DataRadioStationAlarm) {
                    LongClickOnItem((DataRadioStationAlarm) alarm);
                }
                return true;
            }
        });

        alarmsObserver = (o, arg) -> RefreshListAndView();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        RefreshListAndView();

        ram.getSavedAlarmsObservable().addObserver(alarmsObserver);
    }

    @Override
    public void onPause() {
        super.onPause();

        ram.getSavedAlarmsObservable().deleteObserver(alarmsObserver);
    }

    private void RefreshListAndView() {
        adapterRadioAlarm.clear();
        adapterRadioAlarm.addAll(ram.getList());
    }

    DataRadioStationAlarm clickedAlarm = null;
    private void ClickOnItem(DataRadioStationAlarm anObject) {
        clickedAlarm = anObject;
        TimePickerFragment newFragment = new TimePickerFragment();
        newFragment.setCallback(this);
        newFragment.show(getActivity().getSupportFragmentManager(), "timePicker");
    }

    private void LongClickOnItem(DataRadioStationAlarm alarm) {
        Context context = getActivity().getApplicationContext();

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean isNextAlarmSkipped = !sharedPref.getBoolean("alarm_skipped_"+alarm.id, false);

        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("alarm_skipped_"+alarm.id, isNextAlarmSkipped);
        editor.commit();

        Toast toast = Toast.makeText(context, isNextAlarmSkipped ? getString(R.string.alarm_next_skipped) : getString(R.string.alarm_next_unskipped), Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        ram.changeTime(clickedAlarm.id,hourOfDay,minute);
        view.invalidate();
    }

    public RadioAlarmManager getRam() {
        return ram;
    }
}