package babybear.akbquiz;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.weibo.sdk.android.Weibo;
import com.weibo.sdk.android.WeiboException;
import com.weibo.sdk.android.api.StatusesAPI;
import com.weibo.sdk.android.net.RequestListener;
import com.weibo.sdk.android.util.Utility;

public class Quiz extends Activity {
	private static final String TAG = "Quiz";
	private static final long[] v_right = { 100, 100, 100, 100 }, v_wrong = {
			100, 300, 100, 500 };
	private static final String[] ColName = { Database.ColName_ANSWER,
			Database.ColName_WRONG1, Database.ColName_WRONG2,
			Database.ColName_WRONG3 };

	// 问题相关
	private static ArrayList<ContentValues> quizList = null;
	private static boolean isPlaying = false;
	private int correct_answer = 0;
	private int difficulty = 0;
	String[] groups;

	// 界面
	private Button[] Buttons = new Button[4];
	private TextView quiz_Question = null;
	private TextView quiz_Title = null;

	// Loading 界面
	private ProgressBar loading = null;

	// 正误动画
	private PopupWindow Right = null, Wrong = null;
	private Animation rightAnim = null, wrongAnim = null;

	// 震动
	private Vibrator vibrator;
	private boolean isVibratorOn = true;

	// 计数器、计时器、指针
	private Timer timer = new Timer(true);
	private int right_count = 0, wrong_count = 0, time_count = 0;
	private static int quizIndex = 0;

	QuizHandler handler;
	private Dialog dialog;
	private Database db;
	SharedPreferences sp_cfg;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.loading);

		handler = new QuizHandler(this);
		right_count = 0;
		wrong_count = 0;
		time_count = 0;

		db = new Database(Quiz.this, Database.DBName_quiz);

		sp_cfg = getSharedPreferences("config", Context.MODE_PRIVATE);
		isVibratorOn = sp_cfg.getBoolean(Database.KEY_switch_vibration, true);
		vibrator = (Vibrator) this.getSystemService(Service.VIBRATOR_SERVICE);

		LayoutInflater mLayoutInflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		View rightView = mLayoutInflater.inflate(R.layout.right, null);
		rightAnim = AnimationUtils.loadAnimation(this, R.anim.show_fade_out);
		rightView.findViewById(R.id.anim_obj).setAnimation(rightAnim);
		Right = new PopupWindow(rightView);
		Right.setWidth(LayoutParams.FILL_PARENT);
		Right.setHeight(LayoutParams.FILL_PARENT);

		View wrongView = mLayoutInflater.inflate(R.layout.wrong, null);
		wrongAnim = AnimationUtils.loadAnimation(this, R.anim.show_fade_out);
		wrongView.findViewById(R.id.anim_obj).setAnimation(wrongAnim);
		Wrong = new PopupWindow(wrongView);
		Wrong.setWidth(LayoutParams.FILL_PARENT);
		Wrong.setHeight(LayoutParams.FILL_PARENT);

		// loading = new ProgressDialog(this);
		// loading.setCancelable(false);
		// loading.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		// loading.setTitle(R.string.quiz_loading);
		// loading.setMax(100);

		loading = (ProgressBar) findViewById(R.id.progress);
		loading.setMax(100);

		Bundle data = this.getIntent().getExtras();
		ArrayList<String> group = new ArrayList<String>();
		if (data.getBoolean(Database.GroupName_AKB48)) {
			group.add(Database.GroupName_AKB48);
		}
		if (data.getBoolean(Database.GroupName_SKE48)) {
			group.add(Database.GroupName_SKE48);
		}
		if (data.getBoolean(Database.GroupName_NMB48)) {
			group.add(Database.GroupName_NMB48);
		}
		if (data.getBoolean(Database.GroupName_HKT48)) {
			group.add(Database.GroupName_HKT48);
		}
		if (data.getBoolean(Database.GroupName_NGZK46)) {
			group.add(Database.GroupName_NGZK46);
		}
		if (data.getBoolean(Database.GroupName_SDN48)) {
			group.add(Database.GroupName_SDN48);
		}
		if (data.getBoolean(Database.GroupName_JKT48)) {
			group.add(Database.GroupName_JKT48);
		}
		if (data.getBoolean(Database.GroupName_SNH48)) {
			group.add(Database.GroupName_SNH48);
		}
		groups = group.toArray(new String[0]);
		// quizList = db.QuizQuery(data.getInt(Database.ColName_DIFFICULTY, 1),
		// groups.toArray(new String[0]));

		if (sp_cfg.getBoolean(Database.KEY_use_custom_background, false)) {
			findViewById(R.id.loading_body).setBackgroundDrawable(Drawable.createFromPath(Environment.getExternalStorageDirectory()
					.getPath()
					+ "/Android/data/" + getPackageName() + "/custom_bg.png"));
		}

		new Thread() {
			String tips;

			@Override
			public void run() {
				Date start = new Date();
				Log.d(TAG, "start query quiz @ " + start.toLocaleString());
				Random r = new Random();

				int t = r.nextInt(Database.QUIZ_RAND_MAX);
				Editor editor = sp_cfg.edit();
				int offset;
				if (t >= Database.QUIZ_DIVIDE_3) {
					offset = sp_cfg.getInt(Database.KEY_tips_quiz, 0);
					tips = db.getTips(Database.QuizType_Normal, offset);
					editor.putInt(Database.KEY_tips_quiz, offset + 1);
					editor.commit();
				} else {
					offset = sp_cfg.getInt(Database.KEY_tips_info, 0);
					if (t < Database.QUIZ_DIVIDE_1) {
						tips = db.getTips(Database.QuizType_Birthday, offset);

					} else if (t < Database.QUIZ_DIVIDE_2) {
						tips = db.getTips(Database.QuizType_Comefrom,
								sp_cfg.getInt(Database.KEY_tips_info, 0));
					} else {
						tips = db.getTips(Database.QuizType_Team,
								sp_cfg.getInt(Database.KEY_tips_info, 0));
					}
					editor.putInt(Database.KEY_tips_info, offset + 1);
					editor.commit();
				}

				handler.post(new Runnable() {
					@Override
					public void run() {
						((TextView) findViewById(R.id.tips)).setText(tips);
						loading.setProgress(20);
					}
				});

				ArrayList<ContentValues> questions;
				questions = db.QuizQuery(groups);

				Date get = new Date();
				Log.d(TAG, "get query quiz @ " + get.toLocaleString() + " in "
						+ (get.getTime() - start.getTime() + " ms"));
				handler.post(new Runnable() {
					@Override
					public void run() {
						loading.setProgress(80);
					}
				});
				quizList = new ArrayList<ContentValues>();

				for (int i = 0, length = questions.size(); i < length; i++) {
					t = r.nextInt(questions.size());
					quizList.add(questions.get(t));
					questions.remove(t);
				}
				quizIndex = 0;
				if (quizList != null && quizList.size() > 0) {
					isPlaying = true;
					timer.scheduleAtFixedRate(t_timer, 1000, 1000);
				}
				Date complete = new Date();
				Log.d(TAG, "complete query quiz @ " + complete.toLocaleString()
						+ " in " + (complete.getTime() - get.getTime() + " ms"));
				handler.post(new Runnable() {
					@Override
					public void run() {
						loading.setProgress(100);
					}
				});
				handler.sendEmptyMessageDelayed(QuizHandler.QUIZ_LOADED, 500);
			}
		}.start();
	}

	private void widgetInit() {

		setContentView(R.layout.quiz);

		Buttons[0] = (Button) findViewById(R.id.button_A);
		Buttons[1] = (Button) findViewById(R.id.button_B);
		Buttons[2] = (Button) findViewById(R.id.button_C);
		Buttons[3] = (Button) findViewById(R.id.button_D);
		Buttons[0].setOnClickListener(l);
		Buttons[1].setOnClickListener(l);
		Buttons[2].setOnClickListener(l);
		Buttons[3].setOnClickListener(l);

		quiz_Question = (TextView) findViewById(R.id.quiz);

		quiz_Title = (TextView) findViewById(R.id.quiz_Title);

		SharedPreferences sp_cfg = getSharedPreferences("config",
				Context.MODE_PRIVATE);

		if (sp_cfg.getBoolean(Database.KEY_use_custom_background, false)) {
			findViewById(R.id.quiz_body).setBackgroundDrawable(Drawable.createFromPath(Environment.getExternalStorageDirectory()
					.getPath()
					+ "/Android/data/" + getPackageName() + "/custom_bg.png"));
		} else {
			findViewById(R.id.quiz_body).setBackgroundResource(R.drawable.background);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		Log.d(TAG, "onStart");
		// getQuiz();
	}

	@Override
	public void onStop() {
		super.onStop();
		if (Right.isShowing()) {
			Right.dismiss();
		}
		if (Wrong.isShowing()) {
			Wrong.dismiss();
		}

	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Intent intent = new Intent();
			setResult(Activity.RESULT_CANCELED, intent);

			finish();
			break;
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	 * 将quiIndex指向的问题加载到界面
	 */
	void setQuiz() {

		if (quizList == null || quizList.size() == 0) {
			Toast.makeText(this, R.string.quiz_noquiz, Toast.LENGTH_SHORT)
					.show();
			return;
		}
		Log.d("Quiz", "get " + quizList.size() + " rows");
		Random r = new Random();

		// correct_answer=r.nextInt(4);

		int temp;
		boolean flag = true;

		Log.d(TAG, "correct_answer = " + correct_answer);

		ContentValues a_quiz = quizList.get(quizIndex);

		quiz_Title.setText(getString(R.string.quiz_title,
				a_quiz.getAsInteger(Database.ColName_id),
				a_quiz.getAsString(Database.ColName_EDITOR)));

		quiz_Question.setText(getString(R.string.quiz_question,
				quizIndex + 1,
				quizList.size(),
				a_quiz.getAsString(Database.ColName_QUESTION)))
				;

		int[] answer_index = new int[4];
		for (int i = 0; i < 4; i++) {
			flag = false;
			do {
				temp = r.nextInt(4);
				for (int j = 0; j < i; j++) {
					if (temp == answer_index[j]) {
						flag = true;
						break;
					}
					flag = false;
				}
			} while (flag);
			if (temp == 0) {
				correct_answer = i;
			}
			answer_index[i] = temp;
			Buttons[i].setText(a_quiz.getAsString(ColName[temp]));
			// Log.d(TAG,"answer_index["+i+"] = "+answer_index[i]);
		}

	}

	/**
	 * 检查选项并quizIndex自增
	 * 
	 * @param answer 0~3
	 */
	void check(int answer) {
		Log.d(TAG, "answer = " + answer);
		quizIndex++;
		if (answer == correct_answer) {
			Right.showAtLocation(findViewById(R.id.quiz_body),
					Gravity.CENTER,
					0,
					0);

			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					Right.dismiss();
					rightAnim.reset();
					if (quizIndex < quizList.size()) {
						setQuiz();
					} else {
						summary();
					}
				}
			}, rightAnim.getDuration());

			rightAnim.startNow();
			if (isVibratorOn) {
				vibrator.vibrate(v_right, -1);
			}
			MainMenu.se.play(MainMenu.se.sound_right);
			right_count++;
			Log.d(TAG, "Right");
		} else {
			Wrong.showAtLocation(findViewById(R.id.quiz_body),
					Gravity.CENTER,
					0,
					0);
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					Wrong.dismiss();
					wrongAnim.reset();
					if (quizIndex < quizList.size()) {
						setQuiz();
					} else {
						summary();
					}
				}
			}, wrongAnim.getDuration());
			wrongAnim.startNow();
			if (isVibratorOn) {
				vibrator.vibrate(v_wrong, -1);
			}
			MainMenu.se.play(MainMenu.se.sound_wrong);
			wrong_count++;
			Log.d(TAG, "Wrong");

		}
	}

	/**
	 * 答题结束统计
	 */
	void summary() {
		Log.d(TAG, "Summary");
		timer.cancel();
		isPlaying = false;
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		LinearLayout layout = (LinearLayout) inflater.inflate(R.layout.summary,
				null);

		TextView right = (TextView) layout.findViewById(R.id.summary_right);
		right.setText("" + right_count);
		TextView wrong = (TextView) layout.findViewById(R.id.summary_wrong);
		wrong.setText("" + wrong_count);

		// float sum_rate=;
		TextView rate = (TextView) layout.findViewById(R.id.summary_rate);
		rate.setText((float) right_count / (right_count + wrong_count) * 100
				+ "%");
		TextView sum = (TextView) layout.findViewById(R.id.summary_sum);
		sum.setText("" + (right_count + wrong_count));
		TextView time = (TextView) layout.findViewById(R.id.summary_time);
		time.setText("" + time_count);

		AlertDialog.Builder summary_bulider = new AlertDialog.Builder(this);

		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_NEGATIVE:
					finishThis();
					break;
				case DialogInterface.BUTTON_NEUTRAL:
					if (MainMenu.weiboAccessToken.isSessionValid()) {
						Weibo.isWifi = Utility.isWifi(Quiz.this);

						String content = String.format(Quiz.this.getString(R.string.weibo_template),
								quizList.size(),
								right_count,
								wrong_count,
								time_count,
								right_count * 100 / (right_count + wrong_count));

						final EditText input = new EditText(Quiz.this);
						input.setText(content);
						AlertDialog.Builder builder = new AlertDialog.Builder(Quiz.this);
						builder.setTitle(R.string.summary_share)
								.setIcon(R.drawable.weibo_logo_48)
								.setView(input)
								.setNegativeButton(android.R.string.cancel,
										new DialogInterface.OnClickListener() {

											@Override
											public void
													onClick(DialogInterface dialog,
															int which) {
												finishThis();
											}

										})
								.setPositiveButton(android.R.string.ok,
										new DialogInterface.OnClickListener() {

											@Override
											public void
													onClick(DialogInterface dialog,
															int which) {
												StatusesAPI status = new StatusesAPI(MainMenu.weiboAccessToken);
												status.update(input.getText()
														.toString(),
														null,
														null,
														new RequestListener() {

															@Override
															public void
																	onComplete(String arg0) {
																handler.sendEmptyMessage(QuizHandler.WEIBO_SUCCESS);
															}

															@Override
															public void
																	onError(WeiboException arg0) {
																handler.sendEmptyMessage(QuizHandler.WEIBO_FAIL);
															}

															@Override
															public void
																	onIOException(IOException arg0) {
															}

														});
											}
										});
						builder.show();

					} else {
						Toast.makeText(Quiz.this,
								R.string.weibo_err_unauth,
								Toast.LENGTH_SHORT).show();
					}

					break;
				}

			}
		};
		dialog = summary_bulider.setNegativeButton(android.R.string.ok,
				listener)
				.setNeutralButton(R.string.summary_share, listener)
				.setTitle(R.string.summary_title)
				.setView(layout)
				.create();
		dialog.show();
	}

	void finishThis() {
		Intent intent = new Intent();
		intent.putExtra("right", right_count);
		intent.putExtra("wrong", wrong_count);
		intent.putExtra("time", time_count);
		setResult(RESULT_OK, intent);
		dialog.dismiss();
		finish();
	}

	OnClickListener l = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			// Log.d(TAG, ""+arg0);\\\
			if (!isPlaying) {
				return;
			}
			rightAnim.reset();
			wrongAnim.reset();
			switch (arg0.getId()) {
			case R.id.button_A:
				check(0);
				break;
			case R.id.button_B:
				check(1);
				break;
			case R.id.button_C:
				check(2);
				break;
			case R.id.button_D:
				check(3);
			}
		}

	};

	private TimerTask t_timer = new TimerTask() {
		@Override
		public void run() {
			time_count++;
		}
	};

	static class QuizHandler extends Handler {
		final static int QUIZ_LOADED = 1;
		final static int WEIBO_SUCCESS = 2;
		final static int WEIBO_FAIL = 3;
		WeakReference<Quiz> activity;

		QuizHandler(Quiz quizActivity) {
			activity = new WeakReference<Quiz>(quizActivity);
		}

		@Override
		public void handleMessage(Message msg) {
			Log.d(TAG, "msg.what: " + msg.what);
			Quiz quizActivity = activity.get();
			switch (msg.what) {
			case QUIZ_LOADED:
				quizActivity.widgetInit();
				quizActivity.setQuiz();
				break;
			case WEIBO_SUCCESS:
				Toast.makeText(quizActivity,
						quizActivity.getString(R.string.weibo_update_success),
						Toast.LENGTH_SHORT).show();
				quizActivity.finishThis();
				break;

			case WEIBO_FAIL:
				Toast.makeText(quizActivity,
						quizActivity.getString(R.string.weibo_update_fail),
						Toast.LENGTH_SHORT).show();
				quizActivity.finishThis();
				break;
			}

		}
	}

}
