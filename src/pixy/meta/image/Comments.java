/**
 * Copyright (c) 2014-2016 by Wen Yu.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Any modifications to this file must keep this entire header intact.
 * 
 * Change History - most recent changes go on top of previous changes
 *
 * Comments.java
 *
 * Who   Date       Description
 * ====  =========  =================================================
 * WY    06Nov2015  Initial creation
 */

package pixy.meta.image;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import pixy.meta.Metadata;
import pixy.meta.MetadataEntry;
import pixy.meta.MetadataType;

public class Comments extends Metadata {
	private Queue<byte[]> queue;
	private List<String> comments;
	
	public Comments() {
		super(MetadataType.COMMENT);
		queue = new LinkedList<byte[]>();
		comments = new ArrayList<String>();
	}
	
	public Comments(List<String> comments) {
		super(MetadataType.COMMENT);
		queue = new LinkedList<byte[]>();
		if(comments == null) throw new IllegalArgumentException("Input is null");
		this.comments = comments;
	}

	public List<String> getComments() {
		ensureDataRead();
		return Collections.unmodifiableList(comments);
	}
	
	public void addComment(byte[] comment) {
		if(comment == null) throw new IllegalArgumentException("Input is null");
		queue.offer(comment);
	}
	
	public void addComment(String comment) {
		if(comment == null) throw new IllegalArgumentException("Input is null");
		comments.add(comment);
	}
	
	public Iterator<MetadataEntry> iterator() {
		ensureDataRead();
		List<MetadataEntry> entries = new ArrayList<MetadataEntry>();
			
		for (String comment : comments)
		    entries.add(new MetadataEntry(comment, "")); // For comments, we set the value to empty string
		
		return Collections.unmodifiableCollection(entries).iterator();
	}
	
	public void read() throws IOException {
		if(queue.size() > 0) {
			for(byte[] comment : queue) {
				try {
					comments.add(new String(comment, "UTF-8"));
				} catch (UnsupportedEncodingException e) {
					throw new UnsupportedEncodingException("UTF-8");
				}
			}
			queue.clear();
		}
	}
}