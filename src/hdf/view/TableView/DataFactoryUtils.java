/*****************************************************************************
 * Copyright by The HDF Group.                                               *
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

package hdf.view.TableView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import hdf.object.Datatype;

/**
 * A class containing utility functions for the various DataXXXFactory classes,
 * such as DataProviderFactory, DataDisplayConverterFactory and
 * DataValidatorFactory.
 *
 * @author Jordan T. Henderson
 * @version 1.0 2/21/2019
 *
 */
public class DataFactoryUtils {

    public static final String errStr = "*ERROR*";
    public static final String nullStr = "Null";

    public static final int COL_TO_BASE_CLASS_MAP_INDEX = 0;
    public static final int CMPD_START_IDX_MAP_INDEX = 1;

    /*
     * Given a list of all the selected datatypes in a compound dataset, as well as
     * a compound datatype, removes the non-selected datatypes from the List of
     * datatypes inside the compound datatype and returns that as a new List.
     */
    public static List<Datatype> filterNonSelectedMembers(List<Datatype> allSelectedMembers, final Datatype compoundType) {
        /*
         * Make sure to make a copy of the compound datatype's member list, as we will
         * make modifications to the list when members aren't selected.
         */
        List<Datatype> selectedTypes = new ArrayList<Datatype>(compoundType.getCompoundMemberTypes());

        /*
         * Among the datatypes within this compound type, only keep the ones that are
         * actually selected in the dataset.
         */
        Iterator<Datatype> localIt = selectedTypes.iterator();
        while (localIt.hasNext()) {
            Datatype curType = localIt.next();

            /*
             * Since the passed in allSelectedMembers list is a flattened out datatype
             * structure, we want to leave the nested compound Datatypes inside our local
             * list of datatypes.
             */
            if (curType.isCompound())
                continue;

            if (!allSelectedMembers.contains(curType))
                localIt.remove();
        }

        return selectedTypes;
    }

    /*
     * TODO: can potentially merge the two functions.
     */
    @SuppressWarnings("unchecked")
    public static HashMap<Integer, Integer>[] buildIndexMaps(List<Datatype> allSelectedTypes, List<Datatype> localSelectedTypes) throws Exception {
        HashMap<Integer, Integer>[] maps = new HashMap[2];
        maps[COL_TO_BASE_CLASS_MAP_INDEX] = new HashMap<Integer, Integer>();
        maps[CMPD_START_IDX_MAP_INDEX] = new HashMap<Integer, Integer>();

        buildColIdxToProviderMap(maps[COL_TO_BASE_CLASS_MAP_INDEX], allSelectedTypes, localSelectedTypes, new int[] { 0 }, new int[] { 0 }, 0);
        buildRelColIdxToStartIdxMap(maps[CMPD_START_IDX_MAP_INDEX], allSelectedTypes, localSelectedTypes, new int[] { 0 }, new int[] { 0 }, 0);

        return maps;
    }

    /*
     * Recursive routine to build a mapping between physical column indices and
     * indices into the base HDFDataProvider array. For example, consider the
     * following compound datatype:
     *
     *  ___________________________________
     * |             Compound              |
     * |___________________________________|
     * |     |     |    Compound     |     |
     * | int | int |_________________| int |
     * |     |     | int | int | int |     |
     * |_____|_____|_____|_____|_____|_____|
     *
     * The CompoundDataProvider would have 4 base HDFDataProviders:
     *
     * [NumericalDataProvider, NumericalDataProvider, CompoundDataProvider, NumericalDataProvider]
     *
     * and the mapping between physical column indices and this array would be:
     *
     * (0=0, 1=1, 2=2, 3=2, 4=2, 5=3)
     *
     * For the nested CompoundDataProvider, the mapping would simply be:
     *
     * (0=0, 1=1, 2=2)
     */
    private static void buildColIdxToProviderMap(HashMap<Integer, Integer> outMap, List<Datatype> allSelectedTypes,
            List<Datatype> localSelectedTypes, int[] curMapIndex, int[] curProviderIndex, int depth) throws Exception {
        /*
         * TODO: nested array of compound types might not be indexed correctly.
         */
        for (int i = 0; i < localSelectedTypes.size(); i++) {
            Datatype curType = localSelectedTypes.get(i);

            if (curType.isCompound()) {
                List<Datatype> cmpdSelectedTypes = filterNonSelectedMembers(allSelectedTypes, curType);

                buildColIdxToProviderMap(outMap, allSelectedTypes, cmpdSelectedTypes, curMapIndex, curProviderIndex, depth + 1);
            }
            else
                outMap.put(curMapIndex[0]++, curProviderIndex[0]);

            if (depth == 0)
                curProviderIndex[0]++;
        }
    }

    /*
     * Recursive routine to build a mapping between relative indices in a compound
     * type and the relative index of the first member of that compound. For
     * example, consider the following compound datatype:
     *
     * TODO:
     */
    private static void buildRelColIdxToStartIdxMap(HashMap<Integer, Integer> outMap, List<Datatype> allSelectedTypes,
            List<Datatype> localSelectedTypes, int[] curMapIndex, int[] curStartIdx, int depth) throws Exception {
        for (int i = 0; i < localSelectedTypes.size(); i++) {
            Datatype curType = localSelectedTypes.get(i);

            if (curType.isCompound()) {
                if (depth == 0)
                    curStartIdx[0] = curMapIndex[0];

                List<Datatype> cmpdSelectedTypes = filterNonSelectedMembers(allSelectedTypes, curType);

                buildRelColIdxToStartIdxMap(outMap, allSelectedTypes, cmpdSelectedTypes, curMapIndex, curStartIdx, depth + 1);
            }
            else {
                if (depth == 0) {
                    outMap.put(curMapIndex[0], curMapIndex[0]);
                    curMapIndex[0]++;
                }
                else
                    outMap.put(curMapIndex[0]++, curStartIdx[0]);
            }
        }
    }

}
