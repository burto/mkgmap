/*
 * Copyright (C) 2008 Steve Ratcliffe
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Steve Ratcliffe
 * Create date: 29-Nov-2008
 */
package uk.me.parabola.mkgmap.osmstyle;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import uk.me.parabola.mkgmap.osmstyle.actions.Action;
import uk.me.parabola.mkgmap.osmstyle.actions.ActionReader;
import uk.me.parabola.mkgmap.osmstyle.eval.SyntaxException;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.GeneralRelation;
import uk.me.parabola.mkgmap.reader.osm.Relation;
import uk.me.parabola.mkgmap.reader.osm.Rule;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.mkgmap.scan.TokenScanner;

import static org.junit.Assert.*;
import org.junit.Test;

public class ActionReaderTest {

	@Test
	public void testSimpleSet() {
		List<Action> actions = readActionsFromString("{set park=yes}");
		assertEquals("one action", 1, actions.size());

		Element el = stdElementRun(actions);

		assertEquals("park overwritten", "yes", el.getTag("park"));
	}

	@Test
	public void testSimpleAdd() {
		List<Action> actions = readActionsFromString("{add park=yes}");
		assertEquals("one action", 1, actions.size());

		Element el = stdElementRun(actions);
		assertEquals("park not overwritten", "no", el.getTag("park"));
	}

	@Test
	public void testRename() {
		List<Action> actions = readActionsFromString("{rename park landarea}");
		assertEquals("one action", 1, actions.size());

		Element el = stdElementRun(actions);
		assertNull("park should be gone", el.getTag("park"));
		assertEquals("park renamed", "no", el.getTag("landarea"));
	}

	@Test
	public void testFreeForm() {
		List<Action> actions = readActionsFromString(" { set web='world wide';" +
				"set ribbon = 'yellow' } ");

		assertEquals("number of actions", 2, actions.size());
		Element el = stdElementRun(actions);
		assertEquals("park not overwritten", "no", el.getTag("park"));
		assertEquals("word with spaces", "world wide", el.getTag("web"));
		assertEquals("yellow ribbon", "yellow", el.getTag("ribbon"));
	}

	/**
	 * Test several commands in the block.  They should all be executed.
	 */
	@Test
	public void testMultipleCommands() {
		List<Action> actions = readActionsFromString(
				"{set park=yes; add fred=other;" +
						"set pooh=bear}");

		assertEquals("number of actions", 3, actions.size());

		Element el = stdElementRun(actions);

		assertEquals("park set to yes", "yes", el.getTag("park"));
		assertEquals("fred set", "other", el.getTag("fred"));
		assertEquals("pooh set", "bear", el.getTag("pooh"));
	}

	@Test(expected = SyntaxException.class)
	public void testInvalidCommand() {
		readActionsFromString("{bad }");
	}

	@Test
	public void testName() {
		List<Action> actions = readActionsFromString("{name '${name} (${ref})' |" +
				"  '${ref}' | '${name}' ; }");
		Element el = makeElement();
		el.addTag("name", "Main St");
		Rule rule = new ActionRule(null, actions);
		rule.resolveType(el);
		assertEquals("just name", "Main St", el.getName());
	}

	@Test
	public void testDoubleName() {
		List<Action> actions = readActionsFromString("{name '${name} (${ref})' |" +
				"  '${ref}' | '${name}' ; " +
				" name 'fred';}");

		Element el = makeElement();
		Rule rule = new ActionRule(null, actions);
		rule.resolveType(el);
		assertEquals("no tags, second action matches", "fred", el.getName());

		el = makeElement();
		el.addTag("ref", "A1");
		rule.resolveType(el);
		assertEquals("just a ref tag", "A1", el.getName());

		el = makeElement();
		el.addTag("ref", "A1");
		el.addTag("name", "Main St");
		rule.resolveType(el);
		assertEquals("ref and name", "Main St (A1)", el.getName());
	}

	@Test
	public void testApplyAction() {
		List<Action> actions = readActionsFromString("{apply {" +
				"add route=bike;" +
				"set foo=bar; }" +
				"}\n");

		Relation rel = makeRelation();
		Rule rule = new ActionRule(null, actions);
		rule.resolveType(rel);

		assertNull("Tag not set on relation", rel.getTag("route"));

		List<Element> elements = rel.getElements();
		Element el1 = elements.get(0);
		assertEquals("route tag added to first", "bike", el1.getTag("route"));
		assertEquals("foo tag set to first", "bar", el1.getTag("foo"));

		Element el2 = elements.get(1);
		assertEquals("route tag added to second", "bike", el2.getTag("route"));
		assertEquals("foo tag set to second", "bar", el2.getTag("foo"));
	}

	@Test
	public void testApplyWithRole() {
		List<Action> actions = readActionsFromString("{apply role=bar {" +
				"add route=bike;" +
				"set foo=bar; }}");

		Relation rel = makeRelation();
		Rule rule = new ActionRule(null, actions);
		rule.resolveType(rel);

		List<Element> elements = rel.getElements();
		Element el1 = elements.get(0);
		assertEquals("route tag added to first", "bike", el1.getTag("route"));
		assertEquals("foo tag set to first", "bar", el1.getTag("foo"));

		Element el2 = elements.get(1);
		assertNull("route tag not added to second element (role=foo)", el2.getTag("route"));
		assertNull("foo tag not set in second element (role=foo)", el2.getTag("foo"));
	}

	/**
	 * When an apply statement runs, then substitutions on the value use
	 * the tags of the relation and not of the sub element.
	 */
	@Test
	public void testApplyWithSubst() {
		List<Action> actions = readActionsFromString("{apply {" +
				"add route='${route_no}';" +
				"}}");

		Relation rel = makeRelation();
		rel.addTag("route_no", "66");
		Element el1 = rel.getElements().get(0);
		el1.addTag("route_no", "42");

		Rule rule = new ActionRule(null, actions);
		rule.resolveType(rel);
		assertEquals("route_no taken from relation tags", "66", el1.getTag("route"));
	}

	private Element stdElementRun(List<Action> actions) {
		Rule rule = new ActionRule(null, actions);
		Element el = makeElement();
		rule.resolveType(el);
		return el;
	}

	/**
	 * Make a standard element for the tests.
	 */
	private Element makeElement() {
		Element el = new Way();
		el.addTag("park", "no");
		el.addTag("test", "1");
		return el;
	}

	private Relation makeRelation() {
		Relation rel = new GeneralRelation();
		rel.addElement("bar", makeElement());
		rel.addElement("foo", makeElement());
		return rel;
	}
	/**
	 * Read a action list from a string.
	 */
	private List<Action> readActionsFromString(String in) {
		Reader sr = new StringReader(in);
		TokenScanner ts = new TokenScanner("string", sr);
		ActionReader ar = new ActionReader(ts);
		return ar.readActions();
	}
}