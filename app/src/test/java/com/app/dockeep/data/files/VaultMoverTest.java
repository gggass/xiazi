package com.app.dockeep.data.files;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Pure JVM tests: exercise the production rename engine without Android or mocked storage. */
public class VaultMoverTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private File root, left, right;
    private final Object lock = new Object();
    @Before public void prepare() throws Exception {
        root = temporary.newFolder("vault"); left = dir(root,"left"); right = dir(root,"right");
    }
    private File dir(File parent, String name) { File f=new File(parent,name); assertTrue(f.mkdir()); return f; }
    private File file(File parent,String name,String text) throws Exception {
        File f=new File(parent,name); Files.write(f.toPath(),text.getBytes(StandardCharsets.UTF_8)); return f;
    }
    private String text(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8); }
    private VaultMover mover() throws Exception { return new VaultMover(root,lock); }
    private void rejected(Throwing operation) throws Exception {
        try { operation.run(); fail("Expected preflight rejection"); } catch(IllegalArgumentException expected) { }
    }
    interface Throwing { void run() throws Exception; }

    @Test public void moveUsesRenameAndPreservesBytesTimeAndInode() throws Exception {
        File source=file(left,"a.bin","unchanged");assertTrue(source.setLastModified(1700000000000L));
        Object inode=Files.getAttribute(source.toPath(),"unix:ino");
        VaultMover.Result result=mover().move(Collections.singletonList(source),right);
        File target=new File(right,"a.bin");
        assertEquals(1,result.moved.size());assertFalse(source.exists());assertEquals("unchanged",text(target));
        assertEquals(1700000000000L,target.lastModified());assertEquals(inode,Files.getAttribute(target.toPath(),"unix:ino"));
    }
    @Test public void reverseDirectionWorks() throws Exception {
        File source=file(right,"b.txt","right to left");mover().move(Collections.singletonList(source),left);
        assertFalse(source.exists());assertEquals("right to left",text(new File(left,"b.txt")));
    }
    @Test public void directoryTreeAndEmptyDirectoriesRemainIntact() throws Exception {
        File folder=dir(left,"folder");File deep=dir(folder,"deep");dir(folder,"empty");file(deep,"a.txt","nested");
        mover().move(Collections.singletonList(folder),right);
        assertFalse(folder.exists());assertEquals("nested",text(new File(right,"folder/deep/a.txt")));
        assertTrue(new File(right,"folder/empty").isDirectory());
    }
    @Test public void fileNameConflictDoesNotReplaceExistingFileOrDirectory() throws Exception {
        File source=file(left,"a.txt","new");file(right,"a.txt","old");dir(right,"a (1).txt");
        VaultMover.Result result=mover().move(Collections.singletonList(source),right);
        assertEquals("old",text(new File(right,"a.txt")));assertTrue(new File(right,"a (1).txt").isDirectory());
        assertEquals("new",text(new File(right,"a (2).txt")));assertTrue(result.moved.get(0).renamed);
    }
    @Test public void dottedFolderKeepsItsWholeNameOnConflict() throws Exception {
        File folder=dir(left,"2026.10");dir(right,"2026.10");
        mover().move(Collections.singletonList(folder),right);assertTrue(new File(right,"2026.10 (1)").isDirectory());
    }
    @Test public void multiSelectionSameNamesGetsIndependentTargets() throws Exception {
        File one=file(left,"same.txt","one");File other=dir(root,"other");File two=file(other,"same.txt","two");
        VaultMover.Result result=mover().move(Arrays.asList(one,two),right);
        assertEquals(2,result.moved.size());assertEquals("one",text(new File(right,"same.txt")));
        assertEquals("two",text(new File(right,"same (1).txt")));
    }
    @Test public void sameDirectorySkipsWithoutRenaming() throws Exception {
        File f=file(left,"a.txt","keep");VaultMover.Result r=mover().move(Collections.singletonList(f),left);
        assertTrue(r.moved.isEmpty());assertEquals(1,r.skipped.size());assertEquals("keep",text(f));
        assertFalse(new File(left,"a (1).txt").exists());
    }
    @Test public void duplicateAndNestedSelectionsOnlyMoveParentOnce() throws Exception {
        File folder=dir(left,"folder");File child=file(folder,"a.txt","keep");
        VaultMover.Result r=mover().move(Arrays.asList(folder,child,folder),right);
        assertEquals(1,r.moved.size());assertEquals(2,r.includedSelections);assertTrue(r.failures.isEmpty());
        assertEquals("keep",text(new File(right,"folder/a.txt")));
    }
    @Test public void selfAndDescendantTargetsFailBeforeAnyMove() throws Exception {
        File f=file(left,"a.txt","keep");File child=dir(left,"child");
        rejected(()->mover().move(Arrays.asList(f,left),child));assertTrue(f.exists());assertTrue(left.isDirectory());
        rejected(()->mover().move(Collections.singletonList(left),left));assertTrue(f.exists());
    }
    @Test public void rootCannotBeMovedInAMixedBatch() throws Exception {
        File f=file(left,"a.txt","keep");rejected(()->mover().move(Arrays.asList(f,root),right));assertTrue(f.exists());
    }
    @Test public void outOfVaultSourceOrTargetRejectsWholeBatch() throws Exception {
        File outside=temporary.newFolder("outside");File ext=file(outside,"external.txt","outside");File f=file(left,"a.txt","keep");
        rejected(()->mover().move(Arrays.asList(f,ext),right));assertTrue(f.exists());assertEquals("outside",text(ext));
        rejected(()->mover().move(Collections.singletonList(f),outside));assertTrue(f.exists());
        rejected(()->mover().move(Collections.singletonList(f),new File(root,"../outside")));assertTrue(f.exists());
    }
    @Test public void missingSourceOrNonDirectoryDestinationDoesNotMoveValidItems() throws Exception {
        File f=file(left,"a.txt","keep");File target=file(root,"not-dir","keep target");
        rejected(()->mover().move(Arrays.asList(f,new File(left,"missing")),right));assertTrue(f.exists());
        rejected(()->mover().move(Collections.singletonList(f),target));assertEquals("keep target",text(target));
        rejected(()->mover().move(Collections.singletonList(f),new File(root,"missing")));assertTrue(f.exists());
    }
    @Test public void symlinkSourcesAndDestinationsAndParentsAreRejected() throws Exception {
        File f=file(left,"a.txt","keep");File link=new File(root,"file-link");Files.createSymbolicLink(link.toPath(),f.toPath());
        rejected(()->mover().move(Collections.singletonList(link),right));assertTrue(f.exists());
        File linkDir=new File(root,"dir-link");Files.createSymbolicLink(linkDir.toPath(),left.toPath());
        rejected(()->mover().move(Collections.singletonList(new File(linkDir,"a.txt")),right));
        rejected(()->mover().move(Collections.singletonList(f),linkDir));assertTrue(f.exists());
    }
    @Test public void danglingCollisionIsNeverOverwritten() throws Exception {
        File f=file(left,"a.txt","new");File link=new File(right,"a.txt");
        Files.createSymbolicLink(link.toPath(),new File(root,"missing").toPath());
        mover().move(Collections.singletonList(f),right);assertTrue(Files.isSymbolicLink(link.toPath()));
        assertEquals("new",text(new File(right,"a (1).txt")));
    }
    @Test public void configuredRootAliasRemainsSupported() throws Exception {
        File alias=new File(temporary.getRoot(),"alias");Files.createSymbolicLink(alias.toPath(),root.toPath());
        file(left,"a.txt","keep");new VaultMover(alias,lock).move(Collections.singletonList(new File(alias,"left/a.txt")),new File(alias,"right"));
        assertEquals("keep",text(new File(right,"a.txt")));
    }
    @Test public void operatingSystemFailurePreservesFailedItemAndReportsPartialResult() throws Exception {
        File good=file(left,"good.txt","good");File bad=file(left,"bad.txt","bad");
        VaultMover injected=new VaultMover(root,lock,(source,target)->!source.equals(bad) && source.renameTo(target));
        VaultMover.Result r=injected.move(Arrays.asList(good,bad),right);
        assertEquals(1,r.moved.size());assertEquals(1,r.failures.size());assertEquals("bad",text(bad));
        assertFalse(new File(right,"bad.txt").exists());assertEquals("good",text(new File(right,"good.txt")));
    }
    @Test public void longUtf8ConflictNameStaysWithinFilesystemLimit() throws Exception {
        String name=String.join("",Collections.nCopies(85,"图"));File f=file(left,name,"new");file(right,name,"old");
        VaultMover.Result r=mover().move(Collections.singletonList(f),right);
        assertTrue(r.moved.get(0).destination.getName().getBytes(StandardCharsets.UTF_8).length<=255);
        assertEquals("new",text(r.moved.get(0).destination));assertEquals("old",text(new File(right,name)));
    }
    @Test public void hiddenTemporarySourceIsNotMovedAndEmptySelectionIsHarmless() throws Exception {
        File good=file(left,"a.txt","keep");File hidden=file(left,".import-incomplete","partial");
        rejected(()->mover().move(Arrays.asList(good,hidden),right));assertTrue(good.exists());assertTrue(hidden.exists());
        assertTrue(mover().move(Collections.emptyList(),right).moved.isEmpty());
    }
}
