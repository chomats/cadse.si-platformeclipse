/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package fr.imag.adele.fede.workspace.si.platformeclipse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.service.datalocation.Location;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.ide.ChooseWorkspaceData;
import org.eclipse.ui.internal.ide.ChooseWorkspaceDialog;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;

import fr.imag.adele.cadse.as.platformide.IPlatformIDE;
import fr.imag.adele.cadse.as.platformide.IPlatformListener;
import fr.imag.adele.cadse.core.CadseDomain;
import fr.imag.adele.cadse.core.CadseException;
import fr.imag.adele.cadse.core.CadseRuntime;
import fr.imag.adele.cadse.core.Item;
import fr.imag.adele.cadse.core.content.ContentItem;
import fr.imag.adele.cadse.si.workspace.uiplatform.swt.dialog.CadseDialog;

/**
 * @generated
 * @overwite:add implements BundleListener
 */
public class PlatformEclipse implements IPlatformIDE, BundleListener {

	/**
	 * The name of the folder containing metadata information for the workspace.
	 */
	public static final String		METADATA_FOLDER			= ".metadata";										//$NON-NLS-1$

	private static final String		VERSION_FILENAME		= "version.ini";									//$NON-NLS-1$

	private static final String		WORKSPACE_VERSION_KEY	= "org.eclipse.core.runtime";						//$NON-NLS-1$

	private static final String		WORKSPACE_VERSION_VALUE	= "1";												//$NON-NLS-1$

	private static final String		PROP_EXIT_CODE			= "eclipse.exitcode";								//$NON-NLS-1$

	List<IPlatformListener>			listeners				= null;
	static private boolean			uiStarted				= false;
	private boolean					resourceStarted;
	BundleContext					cxt;

	private File					_cachedInstanceLocation	= null;
	static private PlatformEclipse	INSTANCE;

	static final String				RESOURCES_BUNDLE_ID		= "org.eclipse.core.resources";
	static final String				UI_BUNDLE_ID			= "org.eclipse.ui.workbench";

	/** The m logger. */
	static Logger					mLogger					= Logger.getLogger("SI.Workspace.PlatformEclipse");

	public PlatformEclipse(BundleContext cxt) {
		this.cxt = cxt;
	}

	public void start() {
		INSTANCE = this;
		mLogger.info("Start");
		// try {
		// // showPrompt();
		// } catch (Exception e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		Bundle resourceBundle = findBundle(RESOURCES_BUNDLE_ID);
		if (resourceBundle != null && resourceBundle.getState() == Bundle.ACTIVE) {
			setResourceStarted();
		}
		Bundle uiBundle = findBundle(UI_BUNDLE_ID);
		if (uiBundle != null && uiBundle.getState() == Bundle.ACTIVE) {
			if (PlatformUI.isWorkbenchRunning()) {
				setUIStarted();
			}
		}

		cxt.addBundleListener(this);
	}

	public void stop() {
		mLogger.info("Stop");
		listeners = null;
		cxt.removeBundleListener(this);

	}

	public void addListener(IPlatformListener l) {
		if (listeners == null) {
			listeners = new ArrayList<IPlatformListener>();
		}
		synchronized (listeners) {
			if (!listeners.contains(l)) {
				listeners.add(l);
			}
		}
	}

	public void removeListener(IPlatformListener l) {
		if (listeners == null) {
			return;
		}
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	public boolean isResourceStarted() {
		return resourceStarted;
	}

	public boolean isUIStarted() {
		return uiStarted;
	}

	public Bundle findBundle(String symbolicName) {
		return Platform.getBundle(symbolicName);
	}

	public List<Bundle> findBundlePrefix(String prefix) {
		Bundle[] b = cxt.getBundles();
		List<Bundle> ret = new ArrayList<Bundle>();
		for (Bundle bundle : b) {
			if (bundle.getSymbolicName().startsWith(prefix)) {
				ret.add(bundle);
			}
		}
		return ret;
	}

	public void bundleChanged(BundleEvent event) {
		Bundle b = event.getBundle();
		if (event.getType() == BundleEvent.STARTED) {
			if (b.getSymbolicName().equals(RESOURCES_BUNDLE_ID)) {
				Runnable r = new Runnable() {
					public void run() {
						Location instanceLoc = Platform.getInstanceLocation();
						while (!instanceLoc.isSet()) {
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
								return;
							}
						}
						setResourceStarted();
					}
				};
				new Thread(r, "Listen InstanceLocation setting").start();
			}
		}

	}

	private void setResourceStarted() {
		mLogger.info("Resource is started...");
		this.resourceStarted = true;
		if (this.listeners == null) {
			return;
		}
		for (IPlatformListener l : this.listeners) {
			try {
				l.resourceStarted();
			} catch (Throwable e) {
				mLogger.log(Level.SEVERE, "Error when call listener " + l, e);
			}
		}
	}

	private void setUIStarted() {
		mLogger.info("UI is started...");
		uiStarted = true;
		if (this.listeners == null) {
			return;
		}
		for (IPlatformListener l : this.listeners) {
			try {
				l.uiStarted();
			} catch (Throwable e) {
				mLogger.log(Level.SEVERE, "Error when call listener " + l, e);
			}
		}
	}

	public static void earlyStartup() {
		uiStarted = true;
		if (INSTANCE != null) {
			INSTANCE.setUIStarted();
		} else {
			Bundle b = Platform.getBundle("org.apache.felix.org.apache.felix.ipojo");
			if (b != null) {
				try {
					if (b.getState() != Bundle.ACTIVE) {
						b.start();
					}

				} catch (BundleException e) {
					logException(e);
				}
			}
			b = Platform.getBundle("fr.imag.adele.cadse.ipojo.autostart");
			if (b != null) {
				try {
					if (b.getState() != Bundle.ACTIVE) {
						b.start();
					}
				} catch (BundleException e) {
					logException(e);
				}
			}
		}
	}

	private static void logException(BundleException e) {
		e.printStackTrace();
	}

	public File getLocation(boolean wait) {
		if (_cachedInstanceLocation == null) {
			Location instanceLoc = Platform.getInstanceLocation();
			if (instanceLoc == null || !instanceLoc.isSet()) {
				if (!resourceStarted) {
					if (wait) {
						final Object lock = new Object();
						this.addListener(new IPlatformListener() {

							public void resourceStarted() {
								synchronized (lock) {
									lock.notify();
								}
							}

							public void uiStarted() {
							}
						});
						synchronized (lock) {
							try {
								lock.wait();
							} catch (InterruptedException e) {
								return null;
							}
						}
					} else {
						return null;
					}
				}

				int inter = 0;
				while (instanceLoc == null || !instanceLoc.isSet()) {
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						inter++;
						if (inter > 10) {
							return null;
						}
					}
					instanceLoc = Platform.getInstanceLocation();
				}
				if (instanceLoc == null || !instanceLoc.isSet()) {
					return null;
				}
			}

			// assert instanceLoc != null && instanceLoc.isSet()

			// This makes the assumption that the instance location is a file:
			// URL
			_cachedInstanceLocation = new File(instanceLoc.getURL().getFile());
		}
		return _cachedInstanceLocation;

	}

	public void waitUI() {
		if (!uiStarted) {
			final Object lock = new Object();
			this.addListener(new IPlatformListener() {

				public void resourceStarted() {
				}

				public void uiStarted() {
					synchronized (lock) {
						lock.notify();
					}
				}
			});
			synchronized (lock) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.equinox.app.IApplication#start(org.eclipse.equinox.app.IApplicationContext
	 *      context)
	 */
	public void showPrompt() throws Exception {
		Display display = createDisplay();

		try {
			Shell shell = new Shell(display, SWT.ON_TOP);

			try {
				checkInstanceLocation(shell);
			} finally {
				if (shell != null) {
					shell.dispose();
				}
			}
		} finally {
			if (display != null) {
				display.dispose();
			}
		}
	}

	/**
	 * Creates the display used by the application.
	 * 
	 * @return the display used by the application
	 */
	protected Display createDisplay() {
		return PlatformUI.createDisplay();
	}

	/**
	 * Return true if a valid workspace path has been set and false otherwise.
	 * Prompt for and set the path if possible and required.
	 * 
	 * @return true if a valid instance location has been set and false
	 *         otherwise
	 */
	private boolean checkInstanceLocation(Shell shell) {
		// -data @none was specified but an ide requires workspace
		Location instanceLoc = Platform.getInstanceLocation();
		if (instanceLoc == null) {
			MessageDialog.openError(shell, IDEWorkbenchMessages.IDEApplication_workspaceMandatoryTitle,
					IDEWorkbenchMessages.IDEApplication_workspaceMandatoryMessage);
			return false;
		}

		if (instanceLoc.isSet()) {
			return true;
		}

		// -data @noDefault or -data not specified, prompt and set
		ChooseWorkspaceData launchData = new ChooseWorkspaceData(instanceLoc.getDefault());

		boolean force = false;
		while (true) {
			URL workspaceUrl = promptForWorkspace(shell, launchData, force);
			if (workspaceUrl == null) {
				return false;
			}

			// if there is an error with the first selection, then force the
			// dialog to open to give the user a chance to correct
			force = true;

			try {
				// the operation will fail if the url is not a valid
				// instance data area, so other checking is unneeded
				if (instanceLoc.setURL(workspaceUrl, true)) {
					launchData.writePersistedData();
					return true;
				}
			} catch (IllegalStateException e) {
				MessageDialog.openError(shell, IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetTitle,
						IDEWorkbenchMessages.IDEApplication_workspaceCannotBeSetMessage);
				return false;
			}

			// by this point it has been determined that the workspace is
			// already in use -- force the user to choose again
			MessageDialog.openError(shell, IDEWorkbenchMessages.IDEApplication_workspaceInUseTitle,
					IDEWorkbenchMessages.IDEApplication_workspaceInUseMessage);
		}
	}

	/**
	 * Open a workspace selection dialog on the argument shell, populating the
	 * argument data with the user's selection. Perform first level validation
	 * on the selection by comparing the version information. This method does
	 * not examine the runtime state (e.g., is the workspace already locked?).
	 * 
	 * @param shell
	 * @param launchData
	 * @param force
	 *            setting to true makes the dialog open regardless of the
	 *            showDialog value
	 * @return An URL storing the selected workspace or null if the user has
	 *         canceled the launch operation.
	 */
	private URL promptForWorkspace(Shell shell, ChooseWorkspaceData launchData, boolean force) {
		URL url = null;
		do {
			// don't use the parent shell to make the dialog a top-level
			// shell. See bug 84881.
			new ChooseWorkspaceDialog(null, launchData, false, true).prompt(force);
			String instancePath = launchData.getSelection();
			if (instancePath == null) {
				return null;
			}

			// the dialog is not forced on the first iteration, but is on every
			// subsequent one -- if there was an error then the user needs to be
			// allowed to fix it
			force = true;

			// 70576: don't accept empty input
			if (instancePath.length() <= 0) {
				MessageDialog.openError(shell, IDEWorkbenchMessages.IDEApplication_workspaceEmptyTitle,
						IDEWorkbenchMessages.IDEApplication_workspaceEmptyMessage);
				continue;
			}

			// create the workspace if it does not already exist
			File workspace = new File(instancePath);
			if (!workspace.exists()) {
				workspace.mkdir();
			}

			try {
				// Don't use File.toURL() since it adds a leading slash that
				// Platform does not
				// handle properly. See bug 54081 for more details.
				String path = workspace.getAbsolutePath().replace(File.separatorChar, '/');
				url = new URL("file", null, path); //$NON-NLS-1$
			} catch (MalformedURLException e) {
				MessageDialog.openError(shell, IDEWorkbenchMessages.IDEApplication_workspaceInvalidTitle,
						IDEWorkbenchMessages.IDEApplication_workspaceInvalidMessage);
				continue;
			}
		} while (!checkValidWorkspace(shell, url));

		return url;
	}

	/**
	 * Return true if the argument directory is ok to use as a workspace and
	 * false otherwise. A version check will be performed, and a confirmation
	 * box may be displayed on the argument shell if an older version is
	 * detected.
	 * 
	 * @return true if the argument URL is ok to use as a workspace and false
	 *         otherwise.
	 */
	private boolean checkValidWorkspace(Shell shell, URL url) {
		// a null url is not a valid workspace
		if (url == null) {
			return false;
		}

		String version = readWorkspaceVersion(url);

		// if the version could not be read, then there is not any existing
		// workspace data to trample, e.g., perhaps its a new directory that
		// is just starting to be used as a workspace
		if (version == null) {
			return true;
		}

		final int ide_version = Integer.parseInt(WORKSPACE_VERSION_VALUE);
		int workspace_version = Integer.parseInt(version);

		// equality test is required since any version difference (newer
		// or older) may result in data being trampled
		if (workspace_version == ide_version) {
			return true;
		}

		// At this point workspace has been detected to be from a version
		// other than the current ide version -- find out if the user wants
		// to use it anyhow.
		String title = IDEWorkbenchMessages.IDEApplication_versionTitle;
		String message = NLS.bind(IDEWorkbenchMessages.IDEApplication_versionMessage, url.getFile());

		MessageBox mbox = new MessageBox(shell, SWT.OK | SWT.CANCEL | SWT.ICON_WARNING | SWT.APPLICATION_MODAL);
		mbox.setText(title);
		mbox.setMessage(message);
		return mbox.open() == SWT.OK;
	}

	/**
	 * Look at the argument URL for the workspace's version information. Return
	 * that version if found and null otherwise.
	 */
	private static String readWorkspaceVersion(URL workspace) {
		File versionFile = getVersionFile(workspace, false);
		if (versionFile == null || !versionFile.exists()) {
			return null;
		}

		try {
			// Although the version file is not spec'ed to be a Java properties
			// file, it happens to follow the same format currently, so using
			// Properties to read it is convenient.
			Properties props = new Properties();
			FileInputStream is = new FileInputStream(versionFile);
			try {
				props.load(is);
			} finally {
				is.close();
			}

			return props.getProperty(WORKSPACE_VERSION_KEY);
		} catch (IOException e) {
			IDEWorkbenchPlugin.log("Could not read version file", new Status( //$NON-NLS-1$
					IStatus.ERROR, IDEWorkbenchPlugin.IDE_WORKBENCH, IStatus.ERROR,
					e.getMessage() == null ? "" : e.getMessage(), //$NON-NLS-1$,
					e));
			return null;
		}
	}

	/**
	 * The version file is stored in the metadata area of the workspace. This
	 * method returns an URL to the file or null if the directory or file does
	 * not exist (and the create parameter is false).
	 * 
	 * @param create
	 *            If the directory and file does not exist this parameter
	 *            controls whether it will be created.
	 * @return An url to the file or null if the version file does not exist or
	 *         could not be created.
	 */
	private static File getVersionFile(URL workspaceUrl, boolean create) {
		if (workspaceUrl == null) {
			return null;
		}

		try {
			// make sure the directory exists
			File metaDir = new File(workspaceUrl.getPath(), METADATA_FOLDER);
			if (!metaDir.exists() && (!create || !metaDir.mkdir())) {
				return null;
			}

			// make sure the file exists
			File versionFile = new File(metaDir, VERSION_FILENAME);
			if (!versionFile.exists() && (!create || !versionFile.createNewFile())) {
				return null;
			}

			return versionFile;
		} catch (IOException e) {
			// cannot log because instance area has not been set
			return null;
		}
	}

	public void activateIDE() {
		// TODO Auto-generated method stub
		
	}

	public void beginRule(Object rule) {
		// TODO Auto-generated method stub
		
	}

	public void copyResource(Item item, String path, URL data)
			throws CadseException {
		// TODO Auto-generated method stub
		
	}

	public void endRule(Object rule) {
		// TODO Auto-generated method stub
		
	}

	public File getLocation() {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean inDevelopmentMode() {
		// TODO Auto-generated method stub
		return false;
	}

	public void log(String type, String message, Throwable e) {
		// TODO Auto-generated method stub
		
	}

	public void log(String type, String message, Throwable e, Item item) {
		// TODO Auto-generated method stub
		
	}

	public void notifieChangedContent(Item item) {
		// TODO Auto-generated method stub
		
	}

	public CadseRuntime[] openDialog(boolean askToErase) {
		return CadseDialog.openDialog(askToErase);
	}

	public void refresh(Item item) {
		// TODO Auto-generated method stub
		
	}

	public void setItemPersistenceID(String projectName, Item item)
			throws CadseException {
		// TODO Auto-generated method stub
		
	}

	public void setReadOnly(Item item, boolean readonly) {
		// TODO Auto-generated method stub
		
	}
	
	public String getRessourceName(ContentItem contentItem) {
		IResource r = contentItem.getMainMappingContent(IResource.class);
		if (r != null)
			return r.getName();
		File f = contentItem.getMainMappingContent(File.class);
		if (f != null)
			return f.getName();
		return null;
	}
}
