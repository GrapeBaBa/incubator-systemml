/**
 * IBM Confidential
 * OCO Source Materials
 * (C) Copyright IBM Corp. 2010, 2014
 * The source code for this program is not published or otherwise divested of its trade secrets, irrespective of what has been deposited with the U.S. Copyright Office.
 */

package com.ibm.bi.dml.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;

import com.ibm.bi.dml.lops.Lop;
import com.ibm.bi.dml.runtime.controlprogram.CVProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.ExternalFunctionProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.ForProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.FunctionProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.IfProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.ParForProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.Program;
import com.ibm.bi.dml.runtime.controlprogram.ProgramBlock;
import com.ibm.bi.dml.runtime.controlprogram.WhileProgramBlock;
import com.ibm.bi.dml.runtime.instructions.Instruction;
import com.ibm.bi.dml.runtime.instructions.MRJobInstruction;
import com.ibm.bi.dml.runtime.instructions.CPInstructions.CPInstruction;

public class Explain 
{
	@SuppressWarnings("unused")
	private static final String _COPYRIGHT = "Licensed Materials - Property of IBM\n(C) Copyright IBM Corp. 2010, 2014\n" +
                                             "US Government Users Restricted Rights - Use, duplication  disclosure restricted by GSA ADP Schedule Contract with IBM Corp.";
	
	
	private static final boolean REPLACE_SPECIAL_CHARACTERS = true;
	
	/**
	 * 
	 * @param rtprog
	 * @return
	 */
	public static String explain( Program rtprog ) 
	{
		StringBuilder sb = new StringBuilder();		
	
		//create header
		sb.append("\nPROGRAM ( size CP/MR = ");
		sb.append(countCompiledInstructions(rtprog, false, true));
		sb.append("/");
		sb.append(countCompiledInstructions(rtprog, true, false));
		sb.append(" )\n");
		
		//explain functions (if exists)
		HashMap<String, FunctionProgramBlock> funcMap = rtprog.getFunctionProgramBlocks();
		if( funcMap != null && funcMap.size()>0 )
		{
			sb.append("--FUNCTIONS\n");
			for( Entry<String, FunctionProgramBlock> e : funcMap.entrySet() )
			{
				String fkey = e.getKey();
				FunctionProgramBlock fpb = e.getValue();
				if( fpb instanceof ExternalFunctionProgramBlock )
					sb.append("----EXTERNAL FUNCTION "+fkey+"\n");
				else
				{
					sb.append("----FUNCTION "+fkey+"\n");
					for( ProgramBlock pb : fpb.getChildBlocks() )
						sb.append( explainProgramBlock(pb,3) );
				}
			}
			
		}
		
		//explain main program
		sb.append("--MAIN PROGRAM\n");
		for( ProgramBlock pb : rtprog.getProgramBlocks() )
			sb.append( explainProgramBlock(pb,2) );
		
		return sb.toString();	
	}
	
	/**
	 * 
	 * @param pb
	 * @return
	 */
	public static String explain( ProgramBlock pb )
	{
		return explainProgramBlock(pb, 0);
	}
	
	/**
	 * Counts the number of compiled MRJob instructions in the
	 * given runtime program.
	 * 
	 * @param rtprog
	 * @return
	 */
	public static int countCompiledMRJobs( Program rtprog )
	{
		return countCompiledInstructions(rtprog, true, false);
	}
	
	/**
	 * 
	 * @param pb
	 * @param level
	 * @return
	 */
	private static String explainProgramBlock( ProgramBlock pb, int level ) 
	{
		StringBuilder sb = new StringBuilder();
		String offset = createOffset(level);
		
		if (pb instanceof FunctionProgramBlock )
		{
			
			FunctionProgramBlock fpb = (FunctionProgramBlock)pb;
			for( ProgramBlock pbc : fpb.getChildBlocks() )
				sb.append( explainProgramBlock( pbc, level+1) );
		}
		else if (pb instanceof WhileProgramBlock)
		{
			WhileProgramBlock wpb = (WhileProgramBlock) pb;
			sb.append(offset);
			sb.append("WHILE\n");
			sb.append(explainInstructions(wpb.getPredicate(), level+1));			
			for( ProgramBlock pbc : wpb.getChildBlocks() )
				sb.append( explainProgramBlock( pbc, level+1) );
		}	
		else if (pb instanceof IfProgramBlock)
		{
			IfProgramBlock ipb = (IfProgramBlock) pb;
			sb.append(offset);
			sb.append("IF\n");
			sb.append(explainInstructions(ipb.getPredicate(), level+1));
			for( ProgramBlock pbc : ipb.getChildBlocksIfBody() ) 
				sb.append( explainProgramBlock( pbc, level+1) );
			if( ipb.getChildBlocksElseBody().size()>0 )
			{	
				sb.append(offset);
				sb.append("ELSE\n");
				for( ProgramBlock pbc : ipb.getChildBlocksElseBody() ) 
					sb.append( explainProgramBlock( pbc, level+1) );
			}
		}
		else if (pb instanceof ForProgramBlock) //incl parfor
		{
			ForProgramBlock fpb = (ForProgramBlock) pb;
			sb.append(offset);
			if( pb instanceof ParForProgramBlock )
				sb.append("PARFOR\n");
			else
				sb.append("FOR\n");
			sb.append(explainInstructions(fpb.getFromInstructions(), level+1));
			sb.append(explainInstructions(fpb.getToInstructions(), level+1));
			sb.append(explainInstructions(fpb.getIncrementInstructions(), level+1));
			for( ProgramBlock pbc : fpb.getChildBlocks() ) 
				sb.append( explainProgramBlock( pbc, level+1) );
			
		}
		else
		{
			sb.append(offset);
			if( pb.getStatementBlock()!=null )
				sb.append("GENERIC [recompile="+pb.getStatementBlock().requiresRecompilation()+"]\n");
			else
				sb.append("GENERIC\n");
			sb.append(explainInstructions(pb.getInstructions(), level+1));
		}
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param instSet
	 * @param level
	 * @return
	 */
	private static String explainInstructions( ArrayList<Instruction> instSet, int level )
	{
		StringBuilder sb = new StringBuilder();
		String offsetInst = createOffset(level);
		
		for( Instruction inst : instSet )
		{
			String tmp = null;
			if( inst instanceof MRJobInstruction )
				tmp = explainMRJobInstruction((MRJobInstruction)inst, level+1);
			else
				tmp = inst.toString();
			
			inst.toString();
			if( REPLACE_SPECIAL_CHARACTERS ){
				tmp = tmp.replaceAll(Lop.OPERAND_DELIMITOR, " ");
				tmp = tmp.replaceAll(Lop.DATATYPE_PREFIX, ".");
				tmp = tmp.replaceAll(Lop.INSTRUCTION_DELIMITOR, ", ");
			}
			
			sb.append( offsetInst );
			sb.append( tmp );
			sb.append( '\n' );
		}
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param inst
	 * @param level
	 * @return
	 */
	private static String explainMRJobInstruction( MRJobInstruction inst, int level )
	{		
		String instruction = "MR-Job[\n";
		String offset = createOffset(level+1);
		instruction += offset+"  jobtype        = " + inst.getJobType() + " \n";
		instruction += offset+"  input labels   = " + Arrays.toString(inst.getInputVars()) + " \n";
		instruction += offset+"  recReader inst = " + inst.getIv_recordReaderInstructions() + " \n";
		instruction += offset+"  rand inst      = " + inst.getIv_randInstructions() + " \n";
		instruction += offset+"  mapper inst    = " + inst.getIv_instructionsInMapper() + " \n";
		instruction += offset+"  shuffle inst   = " + inst.getIv_shuffleInstructions() + " \n";
		instruction += offset+"  agg inst       = " + inst.getIv_aggInstructions() + " \n";
		instruction += offset+"  other inst     = " + inst.getIv_otherInstructions() + " \n";
		instruction += offset+"  output labels  = " + Arrays.toString(inst.getOutputVars()) + " \n";
		instruction += offset+"  result indices = " + inst.getString(inst.getIv_resultIndices()) + " \n";
		//instruction += offset+"result dims unknown " + getString(iv_resultDimsUnknown) + " \n";
		instruction += offset+"  num reducers   = " + inst.getIv_numReducers() + " \n";
		instruction += offset+"  replication    = " + inst.getIv_replication() + " ]";
		//instruction += offset+"]\n";
		
		return instruction;
	}
	
	/**
	 * 
	 * @param level
	 * @return
	 */
	private static String createOffset( int level )
	{
		StringBuilder sb = new StringBuilder();
		for( int i=0; i<level; i++ )
			sb.append("--");
		return sb.toString();
	}
	
	/**
	 * 
	 * @param rtprog
	 * @param MR
	 * @param CP
	 * @return
	 */
	private static int countCompiledInstructions( Program rtprog, boolean MR, boolean CP )
	{
		int ret = 0;
		
		//analyze DML-bodied functions
		for( FunctionProgramBlock fpb : rtprog.getFunctionProgramBlocks().values() )
			ret += countCompiledInstructions( fpb, MR, CP );
			
		//analyze main program
		for( ProgramBlock pb : rtprog.getProgramBlocks() ) 
			ret += countCompiledInstructions( pb, MR, CP ); 
		
		return ret;
	}
	
	/**
	 * Recursively counts the number of compiled MRJob instructions in the
	 * given runtime program block. 
	 * 
	 * @param pb
	 * @return
	 */
	private static int countCompiledInstructions(ProgramBlock pb, boolean MR, boolean CP) 
	{
		int ret = 0;

		if (pb instanceof WhileProgramBlock)
		{
			WhileProgramBlock tmp = (WhileProgramBlock)pb;
			ret += countCompiledInstructions(tmp.getPredicate(), MR, CP);
			for (ProgramBlock pb2 : tmp.getChildBlocks())
				ret += countCompiledInstructions(pb2,MR,CP);
		}
		else if (pb instanceof IfProgramBlock)
		{
			IfProgramBlock tmp = (IfProgramBlock)pb;	
			ret += countCompiledInstructions(tmp.getPredicate(), MR, CP);
			for( ProgramBlock pb2 : tmp.getChildBlocksIfBody() )
				ret += countCompiledInstructions(pb2,MR,CP);
			for( ProgramBlock pb2 : tmp.getChildBlocksElseBody() )
				ret += countCompiledInstructions(pb2,MR,CP);
		}
		else if (pb instanceof ForProgramBlock) //includes ParFORProgramBlock
		{ 
			ForProgramBlock tmp = (ForProgramBlock)pb;	
			ret += countCompiledInstructions(tmp.getFromInstructions(), MR, CP);
			ret += countCompiledInstructions(tmp.getToInstructions(), MR, CP);
			ret += countCompiledInstructions(tmp.getIncrementInstructions(), MR, CP);
			for( ProgramBlock pb2 : tmp.getChildBlocks() )
				ret += countCompiledInstructions(pb2,MR,CP);
			//additional parfor jobs counted during runtime
		}		
		else if (  pb instanceof FunctionProgramBlock //includes ExternalFunctionProgramBlock and ExternalFunctionProgramBlockCP
			    || pb instanceof CVProgramBlock
				//|| pb instanceof ELProgramBlock
				//|| pb instanceof ELUseProgramBlock
				)
		{
			FunctionProgramBlock fpb = (FunctionProgramBlock)pb;
			for( ProgramBlock pb2 : fpb.getChildBlocks() )
				ret += countCompiledInstructions(pb2,MR,CP);
		}
		else 
		{
			ret += countCompiledInstructions(pb.getInstructions(), MR, CP);
		}
		
		return ret;
	}
	
	/**
	 * 
	 * @param instSet
	 * @param MR
	 * @param CP
	 * @return
	 */
	private static int countCompiledInstructions( ArrayList<Instruction> instSet, boolean MR, boolean CP )
	{
		int ret = 0;
		
		for( Instruction inst : instSet )
		{
			if( MR && inst instanceof MRJobInstruction ) 
				ret++;
			if( CP && inst instanceof CPInstruction )
				ret++;
		}
		
		return ret;
	}
	
}