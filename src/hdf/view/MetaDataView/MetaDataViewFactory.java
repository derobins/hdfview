/*****************************************************************************
 * Copyright by The HDF Group.                                               *
 * Copyright by the Board of Trustees of the University of Illinois.         *
 * All rights reserved.                                                      *
 *                                                                           *
 * This file is part of the HDF Java Products distribution.                  *
 * The full copyright notice, including terms governing use, modification,   *
 * and redistribution, is contained in the files COPYING and Copyright.html. *
 * COPYING can be found at the root of the source code distribution tree.    *
 * Or, see https://support.hdfgroup.org/products/licenses.html               *
 * If you do not have access to either file, you may request a copy from     *
 * help@hdfgroup.org.                                                        *
 ****************************************************************************/

package hdf.view.MetaDataView;

import java.util.HashMap;
import java.util.List;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import hdf.object.Dataset;
import hdf.object.Datatype;
import hdf.object.Group;
import hdf.object.HObject;
import hdf.object.h5.H5Link;
import hdf.view.DataViewFactory;
import hdf.view.Tools;
import hdf.view.ViewManager;
import hdf.view.ViewProperties;
import hdf.view.ImageView.ImageView;
import hdf.view.PaletteView.PaletteView;
import hdf.view.TableView.TableView;
import hdf.view.TreeView.TreeView;

/**
 * A Factory class to return instances of classes implementing the MetaDataView
 * interface, depending on the "currently selected" MetaDataView class in the
 * list maintained by the ViewProperties class.
 *
 * @author jhenderson
 * @version 1.0 4/18/2018
 */
public class MetaDataViewFactory extends DataViewFactory {

    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MetaDataViewFactory.class);

    @SuppressWarnings("rawtypes")
    @Override
    public TableView getTableView(ViewManager viewer, HashMap dataPropertiesMap) throws ClassNotFoundException {
        return null;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public ImageView getImageView(ViewManager viewer, HashMap dataPropertiesMap) throws ClassNotFoundException {
        return null;
    }

    @Override
    public PaletteView getPaletteView(Shell parent, ViewManager viewer, ImageView theImageView) throws ClassNotFoundException {
        return null;
    }

    @Override
    public MetaDataView getMetaDataView(Composite parentObj, ViewManager viewer, HObject theObj) throws ClassNotFoundException {
        String dataViewName = null;
        Object[] initargs = { parentObj, viewer, theObj };
        MetaDataView theView = null;

        log.trace("getMetaDataView(): start");

        /* Retrieve the "currently selected" MetaDataView class to use */
        List<?> metaDataViewList = ViewProperties.getMetaDataViewList();
        if ((metaDataViewList == null) || (metaDataViewList.size() <= 0)) {
            return null;
        }

        dataViewName = (String) metaDataViewList.get(0);

        /*
         * TODO: hard-coded until module loading is enabled in order to avoid class load failures for default classes
         */
        if (theObj instanceof Group)
            dataViewName = ViewProperties.DEFAULT_GROUP_METADATAVIEW_NAME;
        else if (theObj instanceof Dataset)
            dataViewName = ViewProperties.DEFAULT_DATASET_METADATAVIEW_NAME;
        else if (theObj instanceof Datatype)
            dataViewName = ViewProperties.DEFAULT_DATATYPE_METADATAVIEW_NAME;
        else if (theObj instanceof H5Link)
            dataViewName = ViewProperties.DEFAULT_LINK_METADATAVIEW_NAME;
        else
            dataViewName = null;

        /* Attempt to load the class by name */
        Class<?> theClass = null;
        try {
            log.trace("getMetaDataView(): Class.forName({})", dataViewName);

            /* Attempt to load the class by the given name */
            theClass = Class.forName(dataViewName);
        }
        catch (Exception ex) {
            log.debug("getMetaDataView(): Class.forName({}) failure:", dataViewName, ex);

            try {
                log.trace("getMetaDataView(): ViewProperties.loadExtClass().loadClass({})",
                        dataViewName);

                /* Attempt to load the class as an external module */
                theClass = ViewProperties.loadExtClass().loadClass(dataViewName);
            }
            catch (Exception ex2) {
                log.debug(
                        "getMetaDataView(): ViewProperties.loadExtClass().loadClass({}) failure:",
                        dataViewName, ex);

                /* No loadable class found; use the default MetaDataView */
                if (theObj instanceof Group)
                    dataViewName = ViewProperties.DEFAULT_GROUP_METADATAVIEW_NAME;
                else if (theObj instanceof Dataset)
                    dataViewName = ViewProperties.DEFAULT_DATASET_METADATAVIEW_NAME;
                else if (theObj instanceof Datatype)
                    dataViewName = ViewProperties.DEFAULT_DATATYPE_METADATAVIEW_NAME;
                else if (theObj instanceof H5Link)
                    dataViewName = ViewProperties.DEFAULT_LINK_METADATAVIEW_NAME;
                else
                    dataViewName = null;

                try {
                    log.trace("getMetaDataView(): Class.forName({})", dataViewName);

                    theClass = Class.forName(dataViewName);
                }
                catch (Exception ex3) {
                    log.debug("getMetaDataView(): Class.forName({}) failure:", dataViewName,
                            ex);

                    theClass = null;
                }
            }
        }

        if (theClass == null) throw new ClassNotFoundException();

        try {
            theView = (MetaDataView) Tools.newInstance(theClass, initargs);

            log.trace("getMetaDataView(): returning MetaDataView instance {}", theView);
        }
        catch (Exception ex) {
            log.debug("getMetaDataView(): Error instantiating class:", ex);
            theView = null;
        }

        log.trace("getMetaDataView(): finish");

        return theView;
    }

    @Override
    public TreeView getTreeView(Composite parent, ViewManager viewer) throws ClassNotFoundException {
        return null;
    }

}