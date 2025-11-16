package com.crdt.service;

import com.crdt.model.CrdtOperation;
import com.crdt.model.FileNode;
import lombok.Value;

/**
 * A wrapper class to return both the affected FileNode and the
 * generated CrdtOperation from a service method.
 */
@Value
public class CrdtServiceResult {
    FileNode fileNode;
    CrdtOperation operation;
}
