/**
 * @author     Vince Lee, Alex Craig, Mark Aronin
 * @version     1.0                               
 * @since       03.30.12
 */

package Provider.GoogleMapsStatic.TestUI;

import java.util.*;
import java.io.*;

import Provider.GoogleMapsStatic.*;
import Provider.GoogleMapsStatic.TestUI.SampleApp.NewLocation;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.text.*;
import java.util.concurrent.*;

/** 
 * This portion is made by Nazmul from developerlife.com*/
public class SampleApp extends JFrame implements ChangeListener {
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// data members
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	/** reference to task */
	private SimpleTask _task;
	/** this might be null. holds the image to display in a popup */
	private BufferedImage _img;
	/** this might be null. holds the text in case image doesn't display */
	private String _respStr;

	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// main method...
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	public static void main(String[] args) {
		Utils.createInEDT(SampleApp.class);
	}

	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// constructor
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	private void doInit() {
		GUIUtils.setAppIcon(this, "burn.png");
		GUIUtils.centerOnScreen(this);
		setVisible(true);

		int W = 28, H = W;
		boolean blur = false;
		float alpha = .7f;

		try {
			btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W,
					H, blur, alpha));
			btnQuit.setIcon(ImageUtils.loadScaledBufferedIcon("charging.png",
					W, H, blur, alpha));
		} catch (Exception e) {
			System.out.println(e);
		}

		_setupTask();
	}

	/**
	 * create a test task and wire it up with a task handler that dumps output
	 * to the textarea
	 */
	@SuppressWarnings("unchecked")
	private void _setupTask() {

		TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
			public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
					SwingUIHookAdapter hook) throws Exception {

				_initHook(hook);

				// set the license key
				MapLookup.setLicenseKey(ttfLicense.getText());
				// get the uri for the static map
				String uri = MapLookup.getMap(
						Double.parseDouble(ttfLat.getText()),
						Double.parseDouble(ttfLon.getText()),
						Integer.parseInt(ttfSizeW.getText()),
						Integer.parseInt(ttfSizeH.getText()),
						Integer.parseInt(ttfZoom.getText()));
				sout("Google Maps URI=" + uri);

				// get the map from Google
				GetMethod get = new GetMethod(uri);
				new HttpClient().executeMethod(get);

				ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

				try {
					_img = ImageUtils.toCompatibleImage(ImageIO.read(data
							.getInputStream()));
					sout("converted downloaded data to image...");
				} catch (Exception e) {
					_img = null;
					sout("The URI is not an image. Data is downloaded, can't display it as an image.");
					_respStr = new String(data.getBytes());
				}

				return data;
			}

			@Override
			public String getName() {
				return _task.getName();
			}
		};

		_task = new SimpleTask(new TaskManager(), functor, "HTTP GET Task",
				"Download an image from a URL", AutoShutdownSignals.Daemon);

		_task.addStatusListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(":: task status change - "
						+ ProgressMonitorUtils.parseStatusMessageFrom(evt));
				lblProgressStatus.setText(ProgressMonitorUtils
						.parseStatusMessageFrom(evt));
			}
		});

		_task.setTaskHandler(new SimpleTaskHandler<ByteBuffer>() {
			@Override
			public void beforeStart(AbstractTask task) {
				sout(":: taskHandler - beforeStart");
			}

			@Override
			public void started(AbstractTask task) {
				sout(":: taskHandler - started ");
			}

			/**
			 * {@link SampleApp#_initHook} adds the task status listener, which
			 * is removed here
			 */
			@Override
			public void stopped(long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- stopped");
				sout(":: time = " + time / 1000f + "sec");
				task.getUIHook().clearAllStatusListeners();
			}

			@Override
			public void interrupted(Throwable e, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- interrupted - "
						+ e.toString());
			}

			@Override
			public void ok(ByteBuffer value, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- ok - size="
						+ (value == null ? "null" : value.toString()));
				if (_img != null) {
					_displayImgInFrame();
				} else
					_displayRespStrInFrame();

			}

			@Override
			public void error(Throwable e, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- error - "
						+ e.toString());
			}

			@Override
			public void cancelled(long time, AbstractTask task) {
				sout(" :: taskHandler [" + task.getName() + "]- cancelled");
			}
		});
	}

	private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {
		hook.enableRecieveStatusNotification(checkboxRecvStatus.isSelected());
		hook.enableSendStatusNotification(checkboxSendStatus.isSelected());

		hook.setProgressMessage(ttfProgressMsg.getText());

		PropertyChangeListener listener = new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils
						.parseTypeFrom(evt);
				int progress = ProgressMonitorUtils.parsePercentFrom(evt);
				String msg = ProgressMonitorUtils.parseMessageFrom(evt);

				progressBar.setValue(progress);
				progressBar.setString(type.toString());

				sout(msg);
			}
		};

		hook.addRecieveStatusListener(listener);
		hook.addSendStatusListener(listener);
		hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(evt.getPropertyName() + " fired!!!");
			}
		});

		return hook;
	}
	/**
	 * This method displays the imagine within the current container
	 */
	private void _displayImgInFrame() {
		/*
		final JFrame frame = new JFrame("Google Static Map");
		GUIUtils.setAppIcon(frame, "71.png");// Sets the icon as check mark
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JLabel imgLbl = new JLabel(new ImageIcon(_img));
		imgLbl.setToolTipText(MessageFormat.format(
				"<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
				_img.getWidth(), _img.getHeight()));// shows the tooltip when
													// you hover over the map
		imgLbl.addMouseListener(new MouseListener() {// anonymous class
			public void mouseClicked(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
				frame.dispose();
			}// closes the windows if it gets clicked

			public void mouseReleased(MouseEvent e) {
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}
		});

		frame.setContentPane(imgLbl);
		frame.pack();

		GUIUtils.centerOnScreen(frame);
		frame.setVisible(true);*/
		mapPanel.removeAll();
		mapPanel.repaint();
		JLabel imgLbl=new JLabel(new ImageIcon(_img));
		mapPanel.add(imgLbl);
	}
	/**
	 * This method displays errors if errors occur in a pop up.
	 */
	private void _displayRespStrInFrame() {

		final JFrame frame = new JFrame("Google Static Map - Error");
		GUIUtils.setAppIcon(frame, "69.png");
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JTextArea response = new JTextArea(_respStr, 25, 80);
		response.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
				frame.dispose();
			}

			public void mouseReleased(MouseEvent e) {
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}
		});

		frame.setContentPane(new JScrollPane(response));
		frame.pack();

		GUIUtils.centerOnScreen(frame);
		frame.setVisible(true);
	}

	/** simply dump status info to the textarea */
	private void sout(final String s) {
		Runnable soutRunner = new Runnable() {
			public void run() {
				if (ttaStatus.getText().equals("")) {
					ttaStatus.setText(s);
				} else {
					ttaStatus.setText(ttaStatus.getText() + "\n" + s);
				}
			}
		};

		if (ThreadUtils.isInEDT()) {
			soutRunner.run();
		} else {
			SwingUtilities.invokeLater(soutRunner);
		}
	}

	private void startTaskAction() {
		try {
			_task.execute();
		} catch (TaskException e) {
			sout(e.getMessage());
		}
	}

	public SampleApp() {
		initComponents();
		doInit();
	}

	private void quitProgram() {
		_task.shutdown();
		System.exit(0);
	}
	/**
	 * Alex's code: Creates a new pop up window
	 */
	public void createWindow() {
		nameField = new JTextField(15);
		okButton = new JButton("Ok");
		cancelButton = new JButton("Cancel");
		newLocationFrame = new JFrame();
		JPanel panel = new JPanel();
		JLabel message = new JLabel("Enter a name for your location");

		newLocationFrame.setTitle("New Location");
		newLocationFrame.setSize(300, 200);
		newLocationFrame.add(panel);
		GUIUtils.centerOnScreen(newLocationFrame);
		newLocationFrame.setVisible(true);

		panel.setLayout(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();

		c.gridx = 0;
		c.gridy = 0;
		panel.add(message, c);

		c.gridx = 0;
		c.gridy = 1;
		panel.add(nameField, c);

		c.anchor = GridBagConstraints.LINE_START;
		c.gridx = 0;
		c.gridy = 2;
		panel.add(okButton, c);

		c.anchor = GridBagConstraints.LINE_END;
		c.gridx = 0;
		c.gridy = 2;
		panel.add(cancelButton, c);

		ButtonHandler listener = new ButtonHandler();
		okButton.addActionListener(listener);
		cancelButton.addActionListener(listener);
	}
	/**
	 * Alex's code: initializes the combo box with premade locations of the capitals of Canada
	 */
	public void initializeComboBox() {
		savedLocationArray = new ArrayList<NewLocation>();
		locationList = new JComboBox();

		NewLocation[] nl = {new NewLocation("", "", "Select Location"),new NewLocation("43.716589", "-79.340686", "Toronto"), new NewLocation("45.417", "-75.7", "Ottawa"),new NewLocation("46.816667", "-71.216667", "Quebec City"),new NewLocation("44.854444", "-63.199167", "Halifax"),new NewLocation("53.533333", "-113.5", "Edmonton"),new NewLocation("48.422151", "-123.3657", "Victoria"),new NewLocation("49.899444", "-97.139167", "Winnipeg"),new NewLocation("50.454722", "-104.606667", "Regina"),new NewLocation("45.95", "-66.666667", "Fredericton"),new NewLocation("46.24", "-63.1399", "Charlottetown"),new NewLocation("47.5675", "-52.707222", "St. John's")};

		for (int i=0; i < nl.length; i++) {
			savedLocationArray.add(nl[i]);
			locationList.addItem(nl[i].getName());
		}
	}


	private void initComponents() {
		// JFormDesigner - Component initialization - DO NOT MODIFY
		// //GEN-BEGIN:initComponents
		// Generated using JFormDesigner non-commercial license
		dialogPane = new JPanel();
		contentPanel = new JPanel();
		configPanel = new JPanel();
		label2 = new JLabel();
		ttfSizeW = new JTextField();
		label4 = new JLabel();
		ttfLat = new JLabel();
		btnGetMap = new JButton();
		label3 = new JLabel();
		ttfSizeH = new JTextField();
		label5 = new JLabel();
		ttfLon = new JLabel();
		btnQuit = new JButton();
		label1 = new JLabel();
		ttfLicense = new JTextField();
		label6 = new JLabel();
		ttfZoom = new JLabel();
		scrollPane1 = new JScrollPane();
		ttaStatus = new JTextArea();
		statusPanel = new JPanel();
		panel3 = new JPanel();
		zoomPanel = new JPanel();
		zoomPanel2 = new JPanel();
		checkboxRecvStatus = new JCheckBox();
		checkboxSendStatus = new JCheckBox();
		ttfProgressMsg = new JTextField();
		progressBar = new JProgressBar();
		lblProgressStatus = new JLabel();
		LatSlider = new DecimalSlider(-90, 90);
		LonSlider = new DecimalSlider(-180, 180);
		zoomSlider = new JSlider(0, 19);
		saveImage = new JButton();
		saveBox = new JFileChooser();
		saveLocation = new JButton();
		initializeComboBox();

		// ======== this ========
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setTitle("Google Static Maps");
		setIconImage(null);
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());

		// ======== dialogPane ========
		{
			dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
			dialogPane.setOpaque(false);
			dialogPane.setLayout(new BorderLayout());
			zoomPanel2.setBorder(new EmptyBorder(12,12,12,12));
			zoomPanel2.setOpaque(false);
			zoomPanel2.setLayout(new BorderLayout());

			// ======== contentPanel ========
			{
				contentPanel.setOpaque(false);
				contentPanel.setLayout(new TableLayout(new double[][] {
						{ TableLayout.FILL },
						{ TableLayout.PREFERRED, TableLayout.FILL,
							TableLayout.PREFERRED } }));
				((TableLayout) contentPanel.getLayout()).setHGap(5);
				((TableLayout) contentPanel.getLayout()).setVGap(5);

				// ======== configPanel ========
				{
					configPanel.setOpaque(false);
					configPanel.setBorder(new CompoundBorder(new TitledBorder(
							"Configure the inputs to Google Static Maps"),
							Borders.DLU2_BORDER));
					configPanel.setLayout(new TableLayout(new double[][] {
							{ 0.17, 0.17, 0.17, 0.17, 0.05, TableLayout.FILL },
							{ TableLayout.PREFERRED, TableLayout.PREFERRED,
								TableLayout.PREFERRED } }));
					((TableLayout) configPanel.getLayout()).setHGap(5);
					((TableLayout) configPanel.getLayout()).setVGap(5);

					// ---- label2 ----
					label2.setText("Size Width");
					label2.setHorizontalAlignment(SwingConstants.RIGHT);
					configPanel.add(label2, new TableLayoutConstraints(0, 0, 0, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfSizeW ----
					ttfSizeW.setText("512");
					configPanel.add(ttfSizeW, new TableLayoutConstraints(1, 0, 1, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label3 ----
					label3.setText("Size Height");
					label3.setHorizontalAlignment(SwingConstants.RIGHT);
					configPanel.add(label3, new TableLayoutConstraints(0, 1, 0, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					/**
					 * Mark start here: creates a slider with for latidude which has changeListener and 
					 * MouseListener to determine when the slider's value changes and sets the value in the text field 
					 * as well as adding a listener that gets the map whenever a location is selected. Whenever the mouse is released
					 * the map gets refreshed
					 * 
					 */
					
					LatSlider.setBorder(BorderFactory.createTitledBorder("Latitude"));
					LatSlider.setMajorTickSpacing(25);
					LatSlider.setMinorTickSpacing(5);
					LatSlider.setToolTipText("Move the slider to adjust Latitude");
					LatSlider.addChangeListener(this);

					LatSlider.addMouseListener(new MouseAdapter(){
						public void mouseReleased(MouseEvent mouse){
							startTaskAction();
						}
					});

					// A key Listener that checks whether a key has been
					// pressed. example, number keys on the keyboard
					// Partial credit goes to Bart Kiers
					// website source:
					// http://stackoverflow.com/questions/1548606/java-link-jslider-and-jtextfield-for-float-value
					ttfLat.addKeyListener(new KeyAdapter() {// anonymous inner
						// class
						// checks to see when the key has been released
						public void keyReleased(KeyEvent ke) {
							// stores the typed text for latitude inside a
							// variable
							String typed = ttfLat.getText();
							// a regular expression that checks whether the
							// number is a positive decimal number,
							// if doesn't match, it returns nothing and does not
							// change the slider
							if (!typed.matches("(\\+|-)?(\\d+(\\.\\d*)?)")) {

								//LatSlider.setValue((int) 100);
								return;
							}
							double value = Double.parseDouble(typed);
							// sets the value of the typed in number and casts
							// it from a double to an int
							LatSlider.setDoubleValue(value*10000);
							//LatSlider.setValue((int) value*10000);
						}
					});
					/**
					 * Mark start here: creates a slider with for longitude which has changeListener and 
					 * MouseListener to determine when the slider's value changes and sets the value in the text field 
					 * as well as adding a listener that gets the map whenever a location is selected. Whenever the mouse is released
					 * the map gets refreshed
					 * 
					 */
					LonSlider.setBorder(BorderFactory.createTitledBorder("Longitude"));
					LonSlider.setMajorTickSpacing(25);
					LonSlider.setMinorTickSpacing(5);
					LonSlider.setToolTipText("Move the slider to adjust Longitude");
					LonSlider.addChangeListener(this);
					// Partial credit goes to Bart Kiers
					// website source:
					// http://stackoverflow.com/questions/1548606/java-link-jslider-and-jtextfield-for-float-value
					ttfLon.addKeyListener(new KeyAdapter() {
						public void keyReleased(KeyEvent ke) {
							String typed = ttfLon.getText();
							// LatSlider.setValue(0);
							if (!typed.matches("(\\+|-)?(\\d+(\\.\\d*)?)")) {
								return;
							}
							double value = Double.parseDouble(typed);
							LonSlider.setDoubleValue(value*10000);
						}
					});

					LonSlider.addMouseListener(new MouseAdapter(){
						public void mouseReleased(MouseEvent mouse){
							startTaskAction();
						}
					});
					/**
					 * Mark start here: creates a slider with for zooming which has changeListener and 
					 * MouseListener to determine when the slider's value changes and sets the value in the text field 
					 * as well as adding a listener that gets the map whenever a location is selected. Whenever the mouse is released
					 * the map gets refreshed
					 * 
					 */
					//zoomSlider.setOrientation(JSlider.VERTICAL);
					zoomSlider.setBorder(BorderFactory.createTitledBorder("Zoom"));
					zoomSlider.setMajorTickSpacing(5);
					zoomSlider.setMinorTickSpacing(1);
					zoomSlider.addChangeListener(this);
					zoomSlider.setToolTipText("Move the slider to adjust zoom");
					// Partial credit goes to Bart Kiers
					// website source:
					// http://stackoverflow.com/questions/1548606/java-link-jslider-and-jtextfield-for-float-value
					ttfZoom.addKeyListener(new KeyAdapter() {
						public void keyReleased(KeyEvent ke) {
							String typed = ttfZoom.getText();
							if (!typed.matches("\\d+?")) {
								return;
							}
							int value = Integer.parseInt(typed);
							zoomSlider.setValue(value);
						}
					});
					zoomSlider.addMouseListener(new MouseAdapter(){
						public void mouseReleased(MouseEvent mouse){
							startTaskAction();
						}
					});
					//}Mark's Work End

					// ---- ttfSizeH ----
					ttfSizeH.setText("512");
					configPanel.add(ttfSizeH, new TableLayoutConstraints(1, 1, 1, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label4 ----
					label4.setText("Latitude");
					label4.setHorizontalAlignment(SwingConstants.RIGHT);
					configPanel.add(LatSlider, new TableLayoutConstraints(2, 0, 2,
							0, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
					configPanel.add(label2, new TableLayoutConstraints(0, 0, 0, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfLat ----
					ttfLat.setText("0");
					configPanel.add(ttfLat, new TableLayoutConstraints(3, 0, 3, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					/**
					 * Alex's code: creates and add's the combo box to the panel, 
					 * as well as adding a listener that gets the map whenever a location is selected
					 */
					
					//---- locationList combo box ----
					locationList.setSelectedIndex(0);
					locationList.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {

							if (locationList.getSelectedIndex()>0) {

								LatSlider.setDoubleValue(Double.parseDouble(savedLocationArray.get(locationList.getSelectedIndex()).getLatitude())*10000);
								LonSlider.setDoubleValue(Double.parseDouble(savedLocationArray.get(locationList.getSelectedIndex()).getLongitude())*10000);
								startTaskAction();
							}
						}
					});

					configPanel.add(locationList, new TableLayoutConstraints(5, 0, 5, 0, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

					/**
					 * Alex's code: creates and adds the add location button to the panel,
					 * as well as adding a listener to call createWindow()
					 */
					//---- saveLocation button ----
					saveLocation.setText("Add Location");
					saveLocation.setToolTipText("Add the current location to the drop down list (ALT-A)");
					saveLocation.addActionListener (new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							createWindow();
						}
					});
					saveLocation.setMnemonic('a');

					configPanel.add(saveLocation, new TableLayoutConstraints(1, 2, 1, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

					/**
					 * Alex's code:  creates and adds the save image button to the panel,
					 * as well as adding a listener that will pop up the save window and writes the image to the file system
					 */
					//---- saveImage Button ----
					saveImage.setText("Save Map");
					saveImage.setToolTipText("Save the current map to local file system (ALT-S)");
					saveImage.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {

							File saveFile = new File("map.png");
							saveBox.setSelectedFile(saveFile);
							int returnValue = saveBox.showSaveDialog(dialogPane);

							if (returnValue==JFileChooser.APPROVE_OPTION) {
								saveFile = saveBox.getSelectedFile();

								try {
									ImageIO.write(_img, "png", saveFile);
								} 

								catch (IOException ex) {
								}
							}
						}
					});      
					saveImage.setMnemonic('s');
					configPanel.add(saveImage,  new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));

					// ---- btnGetMap ----
					btnGetMap.setText("Get Map");
					btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
					btnGetMap.setToolTipText("Gets the map for currently entered values (ALT-G)");
					btnGetMap.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							startTaskAction();
						}
					});
					btnGetMap.setMnemonic('G');
					configPanel.add(btnGetMap, new TableLayoutConstraints(5, 1, 5,
							1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label5 ----
					label5.setText("Longitude");
					label5.setHorizontalAlignment(SwingConstants.RIGHT);
					configPanel.add(LonSlider, new TableLayoutConstraints(2, 1, 2,
							1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfLon ----
					ttfLon.setText("0");
					configPanel.add(ttfLon, new TableLayoutConstraints(3, 1, 3, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- btnQuit ----
					btnQuit.setText("Quit");
					btnQuit.setToolTipText("Quits the program (ALT-Q)");
					btnQuit.setHorizontalAlignment(SwingConstants.LEFT);
					btnQuit.setHorizontalTextPosition(SwingConstants.RIGHT);
					btnQuit.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							quitProgram();
						}
					});
					btnQuit.setMnemonic('Q');
					configPanel.add(btnQuit, new TableLayoutConstraints(5, 2, 5, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label6 ----
					label6.setText("Zoom");
					label6.setHorizontalAlignment(SwingConstants.RIGHT);
					configPanel.add(zoomSlider, new TableLayoutConstraints(2, 2, 2,2, TableLayoutConstraints.FULL,TableLayoutConstraints.FULL));
					//statusPanel.add(zoomSlider, new TableLayoutConstraints(2, 2, 2,2, TableLayoutConstraints.FULL,TableLayoutConstraints.FULL));

					// ---- ttfZoom ----
					ttfZoom.setText("9");
					configPanel.add(ttfZoom, new TableLayoutConstraints(3, 2, 3, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
				}
				contentPanel.add(configPanel, new TableLayoutConstraints(0, 0, 0, 0,
						TableLayoutConstraints.FULL,
						TableLayoutConstraints.FULL));

				// ======== scrollPane1 ========
				/*{
					scrollPane1
							.setBorder(new TitledBorder(
									"System.out - displays all status and progress messages, etc."));
					scrollPane1.setOpaque(false);

					// ---- ttaStatus ----
					ttaStatus.setBorder(Borders
							.createEmptyBorder("1dlu, 1dlu, 1dlu, 1dlu"));
					ttaStatus
							.setToolTipText("<html>Task progress updates (messages) are displayed here,<br>along with any other output generated by the Task.<html>");
					scrollPane1.setViewportView(ttaStatus);
				}
				contentPanel.add(scrollPane1, new TableLayoutConstraints(0, 1,
						0, 1, TableLayoutConstraints.FULL,
						TableLayoutConstraints.FULL));
				 */
				// ======== statusPanel ========
				{
					statusPanel.setOpaque(false);
					statusPanel.setBorder(new CompoundBorder(new TitledBorder(
							"Status - control progress reporting"),
							Borders.DLU2_BORDER));
					statusPanel.setLayout(new TableLayout(new double[][] {
							{ 0.45, TableLayout.FILL, 0.45 },
							{ TableLayout.PREFERRED, TableLayout.PREFERRED } }));
					((TableLayout) statusPanel.getLayout()).setHGap(5);
					((TableLayout) statusPanel.getLayout()).setVGap(5);

					// ======== panel3 ========
					{
						panel3.setOpaque(false);
						panel3.setLayout(new GridLayout(0, 2));
						//panel3.setBorder(new CompoundBorder(new TitledBorder("Sup bro"), Borders.DLU2_BORDER)); //Random test
						// ---- checkboxRecvStatus ----
						checkboxRecvStatus.setText("Enable \"Recieve\"");
						checkboxRecvStatus.setOpaque(false);
						checkboxRecvStatus
						.setToolTipText("Task will fire \"send\" status updates");
						checkboxRecvStatus.setSelected(true);
						panel3.add(checkboxRecvStatus);

						// ---- checkboxSendStatus ----
						checkboxSendStatus.setText("Enable \"Send\"");
						checkboxSendStatus.setOpaque(false);
						checkboxSendStatus
						.setToolTipText("Task will fire \"recieve\" status updates");
						panel3.add(checkboxSendStatus);
					}

					statusPanel.add(panel3, new TableLayoutConstraints(0, 0, 0, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfProgressMsg ----
					ttfProgressMsg
					.setText("Loading map from Google Static Maps");
					ttfProgressMsg
					.setToolTipText("Set the task progress message here");
					statusPanel.add(ttfProgressMsg, new TableLayoutConstraints(2, 0,
							2, 0, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- progressBar ----
					progressBar.setStringPainted(true);
					progressBar.setString("progress %");
					progressBar.setToolTipText("% progress is displayed here");
					statusPanel.add(progressBar, new TableLayoutConstraints(0, 1, 0,
							1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- lblProgressStatus ----
					lblProgressStatus.setText("task status listener");
					lblProgressStatus
					.setHorizontalTextPosition(SwingConstants.LEFT);
					lblProgressStatus
					.setHorizontalAlignment(SwingConstants.LEFT);
					lblProgressStatus
					.setToolTipText("Task status messages are displayed here when the task runs");
					statusPanel.add(lblProgressStatus, new TableLayoutConstraints(2,
							1, 2, 1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
				}
				contentPanel.add(statusPanel, new TableLayoutConstraints(0, 2, 0, 2, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
				/**
				 * Vince: Creation of map panel to stick into existing container.
				 */
				
				mapPanel=new JPanel();
				mapPanel.setOpaque(false);
				mapPanel.setBorder(new CompoundBorder(new TitledBorder("Map will be displayed here"),Borders.DLU2_BORDER));
				mapPanel.setSize(640,640);
				contentPanel.add(mapPanel,new TableLayoutConstraints(0, 1, 0, 1, TableLayoutConstraints.FULL, TableLayoutConstraints.FULL));
			}
			dialogPane.add(contentPanel, BorderLayout.CENTER);// Positions all the panels in the center of the window

		}
		/**
		 * Adding mouse listeners to ClickMap so that focus can be set onto the map, allowing the added keyboard listener to
		 * pick up the arrow keys.
		 * 
		 * Also, note the automatic resize based on your screen!
		 */
		contentPanel.addMouseListener(new ClickMap());
		contentPanel.addKeyListener(new ArrowKeyActions());
		contentPane.add(dialogPane, BorderLayout.CENTER);
		Toolkit tk =  Toolkit.getDefaultToolkit ();
		Dimension dim = tk.getScreenSize();
		setSize(1024, dim.height-100);
		setLocationRelativeTo(null);
		contentPanel.requestFocusInWindow();
		// JFormDesigner - End of component initialization
		// //GEN-END:initComponents
	}

	// JFormDesigner - Variables declaration - DO NOT MODIFY
	// //GEN-BEGIN:variables
	// Generated using JFormDesigner non-commercial license
	private JPanel dialogPane;
	private JPanel contentPanel;
	private JPanel configPanel;
	private JLabel label2;
	private JTextField ttfSizeW;
	private JLabel label4;
	private JLabel ttfLat;
	private JButton btnGetMap;
	private JLabel label3;
	private JTextField ttfSizeH;
	private JLabel label5;
	private JLabel ttfLon;
	private JButton btnQuit;
	private JLabel label1;
	private JTextField ttfLicense;
	private JLabel label6;
	private JLabel ttfZoom;
	private JScrollPane scrollPane1;
	private JTextArea ttaStatus;
	private JPanel statusPanel;
	private JPanel panel3;
	private JCheckBox checkboxRecvStatus;
	private JCheckBox checkboxSendStatus;
	private JTextField ttfProgressMsg;
	private JProgressBar progressBar;
	private JLabel lblProgressStatus;
	// Other implementations
	//private JSlider LatSlider;
	//private JSlider LonSlider;
	private DecimalSlider LatSlider;
	private DecimalSlider LonSlider;
	private JSlider zoomSlider;
	private JPanel mapPanel;
	private JPanel zoomPanel;
	private JPanel zoomPanel2;
	// JFormDesigner - End of variables declaration //GEN-END:variables
	/*
	 * Alex's variables
	 */
	private JFrame newLocationFrame;
	private JButton cancelButton;
	private JButton okButton;
	private JTextField nameField;
	private JComboBox locationList;
	private JButton saveLocation;
	private JFileChooser saveBox;
	private JButton saveImage;
	private ArrayList <NewLocation> savedLocationArray;

	/**
	 * Alex's code: a new class that will store a maps location and name
	 */
	//---- Classes ----
	public class NewLocation {
		private String latitude;
		private String longitude;
		private String name;

		NewLocation (String lat, String lon, String nam) {
			latitude = lat;
			longitude = lon;
			name = nam;
		}

		public String getLatitude() {
			return latitude;
		}

		public String getLongitude() {
			return longitude;
		}

		public String getName() {
			return name;
		}
	}

	/**
	 * Alex's code: creates a action listener for the buttons in the createWindow() method
	 */
	class ButtonHandler implements ActionListener {

		public void actionPerformed(ActionEvent e) {

			if (e.getSource()==okButton) {

				if (nameField!=null) {
					NewLocation nl = new NewLocation(ttfLat.getText(), ttfLon.getText(), nameField.getText());
					savedLocationArray.add(nl);
					locationList.addItem(nl.getName());
					newLocationFrame.dispose();
				}

			}

			else {
				newLocationFrame.dispose();
			}

		}

	}

	protected void update() {

	}
	//When the state of the slider is changed, change the text values inside the text fields
	public void stateChanged(ChangeEvent e) {
		final DecimalFormat format = new DecimalFormat("0.####");
		ttfZoom.setText("" + zoomSlider.getValue());
		ttfLat.setText(format.format(LatSlider.getDoubleValue()));
		ttfLon.setText(format.format(LonSlider.getDoubleValue()));
		contentPanel.requestFocusInWindow();
	}
	//Class Coded by Mark Aronin with reference to Bart Kiers's code
	/**
	 * Mark's Code: This class is a custom JSlider which sets the values of the Longitude and Latidude to 4 decimal places
	 * 
	 */
	class DecimalSlider extends JSlider{
		final int scale = 10000;
		public DecimalSlider (int min, int max ){
			super(min*10000, max*10000);
		}
		public double getDoubleValue(){
			return ((double)super.getValue()/this.scale);
		}
		void setDoubleValue(double value){
			super.setValue((int)value);
		}
	}
	/**
	 * Keyboard listener to move the map left, right, up, or down by .5. This method could
	 * be further refined to take zoom levels into account....but not enough time =(
	 */
	class ArrowKeyActions implements KeyListener{
		public void keyPressed(KeyEvent e){

		}
		@Override
		public void keyReleased(KeyEvent e) {
			int keyCode=e.getKeyCode();
			Double lat=new Double(Double.parseDouble(ttfLat.getText()));
			Double lon=new Double(Double.parseDouble(ttfLon.getText()));
			final DecimalFormat format = new DecimalFormat("0.####");

			switch(keyCode){
			case KeyEvent.VK_LEFT:
				lon=new Double(lon.doubleValue()-0.5);
				ttfLon.setText(format.format(lon.doubleValue()));
				startTaskAction();
				break;
			case KeyEvent.VK_RIGHT:
				lon=new Double(lon.doubleValue()+0.5);
				ttfLon.setText(format.format(lon.doubleValue()));
				startTaskAction();
				break;
			case KeyEvent.VK_DOWN:
				lat=new Double(lat.doubleValue()-0.5);
				ttfLat.setText(lat.toString());
				startTaskAction();
				break;
			case KeyEvent.VK_UP:
				lat=new Double(lat.doubleValue()+0.5);
				ttfLat.setText(lat.toString());
				startTaskAction();
				break;
			}

		}

		@Override
		public void keyTyped(KeyEvent arg0) {
			// TODO Auto-generated method stub

		}
	}
	/*
	 * Simple mouse listener to allow compliment the keyboard listener. Without this listener it would be impossible
	 * for the user to gain focus on the map and therefore registered listeners would not pick up the keys.
	 */
	class ClickMap implements MouseListener{

		@Override
		public void mouseClicked(MouseEvent arg0) {
			contentPanel.requestFocusInWindow();

		}

		@Override
		public void mouseEntered(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseExited(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mousePressed(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void mouseReleased(MouseEvent arg0) {
			// TODO Auto-generated method stub

		}

	}

}
