/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2013
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.runtime.controlprogram.parfor;

import java.util.Collection;

import com.ibm.bi.dml.runtime.DMLRuntimeException;
import com.ibm.bi.dml.runtime.instructions.CPInstructions.IntObject;

/**
 * This is the base class for all task partitioner. For this purpose it stores relevant information such as
 * the loop specification (FROM, TO, INCR), the index variable and the task size. Furthermore, it declares two
 * prototypes: (1) full task creation, (2) streaming task creation.
 * 
 * Known implementation classes: TaskPartitionerFixedsize, TaskPartitionerFactoring
 * 
 */
public abstract class TaskPartitioner 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2013\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	protected int            _taskSize     = -1;
	
	protected String  		 _iterVarName  = null;
	protected IntObject      _fromVal      = null;
	protected IntObject      _toVal        = null;
	protected IntObject      _incrVal      = null;
	
	protected int            _numIter      = -1;
	
	
	protected TaskPartitioner( int taskSize, String iterVarName, IntObject fromVal, IntObject toVal, IntObject incrVal ) 
	{
		_taskSize    = taskSize;
		
		_iterVarName = iterVarName;
		_fromVal     = fromVal;
		_toVal       = toVal;
		_incrVal     = incrVal;
		
		_numIter     = (int)Math.ceil(((double)(_toVal.getIntValue()-_fromVal.getIntValue()+1 )) / _incrVal.getIntValue()); 
	}
	
	/**
	 * Creates and returns set of all tasks for given problem at once.
	 * 
	 * @return
	 */
	public abstract Collection<Task> createTasks()
		throws DMLRuntimeException;
	
	/**
	 * Creates set of all tasks for given problem, but streams them directly
	 * into task queue. This allows for more tasks than fitting in main memory.
	 * 
	 * @return
	 */
	public abstract int createTasks( LocalTaskQueue<Task> queue )
		throws DMLRuntimeException;

	
	/**
	 * 
	 * @return
	 */
	public int getNumIterations()
	{
		return _numIter;
	}
}