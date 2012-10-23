package com.geeckon;

import android.app.Activity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * https://github.com/Epsiloni/ADKBareMinimum Based on Simon Monk code. Rewritten by Assaf Gamliel (goo.gl/E2MhJ) (assafgamliel.com). Feel free to contact
 * me with any question, I hope I can help. This code should give you a good jump start with your Android and Arduino project. -- This is the minimum you
 * need to communicate between your Android device and your Arduino device. If needed I'll upload the example I made (Sonar distance measureing device).
 */
public class MainActivity extends Activity implements View.OnClickListener, ADKManager.Callback {
    private static final String TAG = "MainActivity";

    private ProgressBar mProgress;
    private ToggleButton mBtnPower;
    private ToggleButton mBtnMotor1;
    private ToggleButton mBtnMotor2;
    private TextView mTextAck;
    private EditText mTextAngle;
    private Button mBtnSendAngle;
    private Button mBtnResetAngle;
    private Button mBtnRotateLeft;
    private Button mBtnRotateRight;
    private Button mBtnStopRotation;

    private ADKManager mADKManager;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        init();
    }

    private void init() {
        mADKManager = new ADKManager(this, this);
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mBtnPower = (ToggleButton) findViewById(R.id.btn_power);
        mBtnMotor1 = (ToggleButton) findViewById(R.id.btn_motor_1);
        mBtnMotor2 = (ToggleButton) findViewById(R.id.btn_motor_2);
        mTextAck = (TextView) findViewById(R.id.ack);
        mTextAngle = (EditText) findViewById(R.id.txt_angle);
        mBtnSendAngle = (Button) findViewById(R.id.btn_send_angle);
        mBtnResetAngle = (Button) findViewById(R.id.btn_reset_angle);
        mBtnRotateLeft = (Button) findViewById(R.id.btn_rotate_left);
        mBtnRotateRight = (Button) findViewById(R.id.btn_rotate_right);
        mBtnStopRotation = (Button) findViewById(R.id.btn_stop_rotation);

        // listeners
        mBtnSendAngle.setOnClickListener(this);
        mBtnResetAngle.setOnClickListener(this);
        mBtnRotateLeft.setOnClickListener(this);
        mBtnRotateRight.setOnClickListener(this);
        mBtnStopRotation.setOnClickListener(this);

        // prepare buttons
        enableButtons(false);
        mBtnPower.setEnabled(true);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_send_angle:
                if (!TextUtils.isEmpty(mTextAngle.getText())) {
                    try {
                        int signedAngle = Integer.parseInt(mTextAngle.getText().toString());
                        byte angle = toUnsignedByte(signedAngle);
                        sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_ROTATE_BY_ANGLE, new byte[] {angle});
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid angle, please use values in range of 0 - 180", Toast.LENGTH_SHORT).show();
                    }
                }
                break;

            case R.id.btn_reset_angle:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_RESET_ROTATION, null);
                break;

            case R.id.btn_rotate_left:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_START_ROTATE, new byte[] {ADKManager.DIRECTION_LEFT});
                break;

            case R.id.btn_rotate_right:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_START_ROTATE, new byte[] {ADKManager.DIRECTION_RIGHT});
                break;

            case R.id.btn_stop_rotation:
                sendCommand(ADKManager.COMMAND_ROTATE, ADKManager.ACTION_END_ROTATE, null);
                break;
        }
    }

    public void onPowerClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(ADKManager.COMMAND_STAND_BY, ADKManager.ON, null);
        } else {
            sendCommand(ADKManager.COMMAND_STAND_BY, ADKManager.OFF, null);
        }
    }

    public void onMotor1Clicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(ADKManager.COMMAND_MOTOR_1, ADKManager.ACTION_POWER_ON, new byte[] {ADKManager.DIRECTION_LEFT, (byte) 255});
        } else {
            sendCommand(ADKManager.COMMAND_MOTOR_1, ADKManager.ACTION_POWER_OFF, null);
        }
    }

    public void onMotor2Clicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(ADKManager.COMMAND_MOTOR_2, ADKManager.ACTION_POWER_ON, new byte[] {ADKManager.DIRECTION_LEFT, (byte) 255});
        } else {
            sendCommand(ADKManager.COMMAND_MOTOR_2, ADKManager.ACTION_POWER_OFF, null);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "Resuming");
        super.onResume();
        mADKManager.connect();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "Application being paused");
        super.onPause();
        mADKManager.disconnect();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Application being destroyed");
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        mProgress.setVisibility(View.VISIBLE);
        enableButtons(false);
        mADKManager.sendCommand(command, action, data);
    }

    @Override
    public void onADKAckReceived(boolean ack) {
        enableButtons(true);
        mProgress.setVisibility(View.GONE);
        mTextAck.setText(String.format("Ack received: %b", ack));
    }

    private void enableButtons(boolean enabled) {
        mBtnPower.setEnabled(enabled);
        mBtnMotor1.setEnabled(enabled);
        mBtnMotor2.setEnabled(enabled);
        mTextAngle.setEnabled(enabled);
        mBtnSendAngle.setEnabled(enabled);
        mBtnResetAngle.setEnabled(enabled);
        mBtnRotateLeft.setEnabled(enabled);
        mBtnRotateRight.setEnabled(enabled);
        mBtnStopRotation.setEnabled(enabled);
    }

    public static byte toUnsignedByte(int b) {
        return (byte) (b & 0xFF);
    }


}