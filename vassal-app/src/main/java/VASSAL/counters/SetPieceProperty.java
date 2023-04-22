/*
 *
 * Copyright (c) 2000-2023 by The Vassal Development Team, Brent Easton, Rodney Kinney, Brian Reynolds
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.counters;

import VASSAL.build.module.Map;
import VASSAL.build.module.documentation.HelpFile;
import VASSAL.build.module.properties.PropertyChanger;
import VASSAL.build.module.properties.PropertyPrompt;
import VASSAL.command.ChangeTracker;
import VASSAL.command.Command;
import VASSAL.command.NullCommand;
import VASSAL.configure.BooleanConfigurer;
import VASSAL.configure.DynamicKeyCommandListConfigurer;
import VASSAL.configure.FormattedExpressionConfigurer;
import VASSAL.configure.IntConfigurer;
import VASSAL.configure.PropertyExpression;
import VASSAL.configure.StringConfigurer;
import VASSAL.configure.TranslatingStringEnumConfigurer;
import VASSAL.i18n.Resources;
import VASSAL.script.expression.AuditTrail;
import VASSAL.tools.FormattedString;
import VASSAL.tools.RecursionLimiter;
import VASSAL.tools.SequenceEncoder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import javax.swing.JLabel;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @author Brian Reynolds, Brent Easton
 *
 * A trait that allows counters to manipulate the value of the Dynamic Properties of OTHER pieces.
 * Combines the Property manipulation functionality of DynamicProperty with the searching function of Global Key Commands
 */
public class SetPieceProperty extends DynamicProperty implements RecursionLimiter.Loopable {
  protected PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);
  public static final String ID = "setpieceprop;"; // NON-NLS
  public static final String INDEX_PROP = "AttachmentIndex"; //NON-NLS
  public static final String ATTACH_PROP = "AttachmentName"; //NON-NLS
  public static final String ATTACH_BASIC = "AttachmentBasicName"; //NON-NLS
  protected String description;

  protected GlobalCommandTarget target = new GlobalCommandTarget(GlobalCommandTarget.GKCtype.COUNTER);
  protected GlobalSetter globalSetter = new GlobalSetter(this);
  protected PropertyExpression propertiesFilter = new PropertyExpression();
  protected boolean restrictRange;
  protected boolean fixedRange = true;
  protected int range;
  protected String rangeProperty = "";

  protected Decorator dec;

  public SetPieceProperty() {
    this(ID, null);
  }

  public SetPieceProperty(String type, GamePiece p) {
    super(type, p);
  }

  @Override
  public String getDescription() {
    return buildDescription("Editor.SetPieceProperty.trait_description", key, description) + getCommandsList();
  }

  @Override
  public String getBaseDescription() {
    return Resources.getString("Editor.SetPieceProperty.trait_description");
  }

  @Override
  public String getDescriptionField() {
    return description;
  }

  @Override
  public void mySetType(String s) {
    final SequenceEncoder.Decoder sd = new SequenceEncoder.Decoder(s, ';');
    sd.nextToken(); // Skip over command prefix
    key = sd.nextToken("name");
    decodeConstraints(sd.nextToken(""));
    keyCommandListConfig.setValue(sd.nextToken(""));
    keyCommands = keyCommandListConfig.getListValue().toArray(new DynamicKeyCommand[0]);

    menuCommands = Arrays.stream(keyCommands).filter(
      kc -> !StringUtils.isEmpty(kc.getName())
    ).toArray(KeyCommand[]::new);

    description = sd.nextToken("");

    target.decode(sd.nextToken(""));
    target.setGKCtype(GlobalCommandTarget.GKCtype.COUNTER);
    target.setCurPiece(this);

    propertiesFilter.setExpression(sd.nextToken(""));
    restrictRange = sd.nextBoolean(false);
    range = sd.nextInt(1);
    fixedRange = sd.nextBoolean(true);
    rangeProperty = sd.nextToken("");
    globalSetter.setSelectFromDeckExpression(sd.nextToken("-1"));
  }

  @Override
  public String myGetType() {
    final SequenceEncoder se = new SequenceEncoder(';');
    se.append(key);
    se.append(encodeConstraints());
    se.append(keyCommandListConfig.getValueString());
    se.append(description);
    se.append(target.encode());
    se.append(propertiesFilter);
    se.append(restrictRange);
    se.append(range);
    se.append(fixedRange);
    se.append(rangeProperty);
    se.append(globalSetter.getSelectFromDeckExpression());
    return ID + se.getValue();
  }

  @Override
  public String myGetState() {
    return "";
  }

  @Override
  public void mySetState(String state) {
  }

  /*
   * Duplicate code from Decorator for setProperty(), getProperty() Do not call super.xxxProperty() as we no longer
   * contain a DynamicProperty that can be manipulated, but you cannot call super.super.xxxProperty().
   */
  @Override
  public Object getProperty(Object key) {
    if (Properties.KEY_COMMANDS.equals(key)) {
      return getKeyCommands();
    }
    else if (Properties.INNER.equals(key)) {
      return piece;
    }
    else if (Properties.OUTER.equals(key)) {
      return dec;
    }
    else if (Properties.VISIBLE_STATE.equals(key)) {
      return myGetState() + piece.getProperty(key);
    }
    else {
      return piece.getProperty(key);
    }
  }

  @Override
  public Object getLocalizedProperty(Object key) {
    if (Properties.KEY_COMMANDS.equals(key)) {
      return getProperty(key);
    }
    else if (Properties.INNER.equals(key)) {
      return getProperty(key);
    }
    else if (Properties.OUTER.equals(key)) {
      return getProperty(key);
    }
    else if (Properties.VISIBLE_STATE.equals(key)) {
      return getProperty(key);
    }
    else {
      return piece.getLocalizedProperty(key);
    }
  }

  @Override
  public void setProperty(Object key, Object val) {
    if (Properties.INNER.equals(key)) {
      setInner((GamePiece) val);
    }
    else if (Properties.OUTER.equals(key)) {
      dec = (Decorator) val;
    }
    else {
      piece.setProperty(key, val);
    }
  }

  @Override
  public HelpFile getHelpFile() {
    return HelpFile.getReferenceManualPage("SetPieceProperty.html"); // NON-NLS
  }

  private String propName;
  private PropertyChanger changer = null;
  private String newValue = null;

  public Command makeSetTargetCommand(GamePiece p) {
    Command comm = new NullCommand();

    while (p instanceof Decorator) {
      if (p instanceof DynamicProperty) {
        final DynamicProperty currentDP = (DynamicProperty)p;

        if (propName.equals(currentDP.getName())) {
          if ((newValue == null) || !(changer instanceof PropertyPrompt)) {
            newValue = changer.getNewValue(currentDP.value);
            // The PropertyChanger has already evaluated any Beanshell, only need to handle any remaining $$variables.
            if (newValue.indexOf('$') >= 0) {
              format.setFormat(newValue);
              newValue = format.getText(this, this, "Editor.PropertyChangeConfigurer.new_value");
            }
          }

          final ChangeTracker ct = new ChangeTracker(currentDP);

          currentDP.value = newValue;

          comm = comm.append(ct.getChangeCommand());
        }
      }

      p = ((Decorator) p).getInner();
    }

    return comm;
  }

  /*
   * Locate the correct property/properties to adjust and update value(s).
   * $xxxx$ names are allowed in any of the property name, attachment name, and attachment index fields
   * Blank fields for attachment name and/or index count as wild cards
   */
  @Override
  public Command myKeyEvent(KeyStroke stroke) {
    final GamePiece outer = Decorator.getOutermost(this);
    globalSetter.setPropertySource(outer); // Doing this here ensures trait is linked into GamePiece before finding source

    Command comm = new NullCommand();
    for (final DynamicKeyCommand keyCommand : keyCommands) {
      if (keyCommand.matches(stroke)) {
        propName = (new FormattedString(key)).getText(Decorator.getOutermost(this), this, "Editor.DynamicProperty.property_name");

        changer = keyCommand.propChanger;
        newValue = null;

        // Make piece properties filter
        final AuditTrail audit = AuditTrail.create(this, propertiesFilter.getExpression(), Resources.getString("Editor.GlobalKeyCommand.matching_properties"));
        PieceFilter filter = propertiesFilter.getFilter(outer, this, audit);

        // Make a range filter if applicable
        if (restrictRange) {
          int r = range;
          if (!fixedRange) {
            final String rangeValue = (String) Decorator.getOutermost(this).getProperty(rangeProperty);
            try {
              r = Integer.parseInt(rangeValue);
            }
            catch (NumberFormatException e) {
              reportDataError(this, Resources.getString("Error.non_number_error"), "range[" + rangeProperty + "]=" + rangeValue, e); // NON-NLS
            }
          }
          filter = new BooleanAndPieceFilter(filter, new RangeFilter(getMap(), getPosition(), r));
        }

        // Now apply our filter globally & add any matching pieces as attachments
        comm = comm.append(globalSetter.apply(Map.getMapList().toArray(new Map[0]), filter, target, audit));

        return comm;
      }
    }
    return comm;
  }

  @Override
  public PieceEditor getEditor() {
    return new Ed(this);
  }

  @Override
  public boolean testEquals(Object o) {
    if (! (o instanceof SetPieceProperty)) return false;
    final SetPieceProperty c = (SetPieceProperty) o;

    if (! Objects.equals(key, c.key)) return false;
    if (! Objects.equals(encodeConstraints(), c.encodeConstraints())) return false;
    if (! Objects.equals(keyCommandListConfig.getValueString(), c.keyCommandListConfig.getValueString())) return false;
    if (! Objects.equals(description, c.description)) return false;
    if (! Objects.equals(target, c.target)) return false;
    if (! Objects.equals(propertiesFilter, c.propertiesFilter)) return false;
    if (! Objects.equals(restrictRange, c.restrictRange)) return false;
    if (! Objects.equals(range, c.range)) return false;
    if (! Objects.equals(fixedRange, c.fixedRange)) return false;
    if (! Objects.equals(rangeProperty, c.rangeProperty)) return false;
    return Objects.equals(globalSetter.getSelectFromDeckExpression(), c.globalSetter.getSelectFromDeckExpression());
  }

  protected static class Ed implements PieceEditor {
    protected StringConfigurer descConfig;
    protected FormattedExpressionConfigurer nameConfig;
    protected BooleanConfigurer numericConfig;
    protected JLabel minLabel;
    protected IntConfigurer minConfig;
    protected JLabel maxLabel;
    protected IntConfigurer maxConfig;
    protected JLabel wrapLabel;
    protected BooleanConfigurer wrapConfig;
    protected DynamicKeyCommandListConfigurer keyCommandListConfig;
    protected TranslatingStringEnumConfigurer levelConfig;
    protected FormattedExpressionConfigurer attachNameConfig;
    protected FormattedExpressionConfigurer attachIndexConfig;
    protected TraitConfigPanel controls;

    public Ed(final SetPieceProperty m) {
      keyCommandListConfig = new DynamicKeyCommandListConfigurer(null, Resources.getString("Editor.DynamicProperty.commands"), m);
      keyCommandListConfig.setValue(new ArrayList<>(Arrays.asList(m.keyCommands)));

      controls = new TraitConfigPanel();

      descConfig = new StringConfigurer(m.description);
      descConfig.setHintKey("Editor.description_hint");
      controls.add("Editor.description_label", descConfig);

      nameConfig = new FormattedExpressionConfigurer(m.getKey(), (EditablePiece) m);
      nameConfig.setHintKey("Editor.SetPieceProperty.property_name_hint");
      controls.add("Editor.SetPieceProperty.property_name", nameConfig);

      numericConfig = new BooleanConfigurer(m.isNumeric());
      controls.add("Editor.DynamicProperty.is_numeric", numericConfig);

      minLabel = new JLabel(Resources.getString("Editor.GlobalProperty.minimum_value"));
      minConfig = new IntConfigurer(m.getMinimumValue());
      controls.add(minLabel, minConfig);

      maxLabel = new JLabel(Resources.getString("Editor.GlobalProperty.maximum_value"));
      maxConfig = new IntConfigurer(m.getMaximumValue());
      controls.add(maxLabel, maxConfig);

      wrapLabel  = new JLabel(Resources.getString("Editor.DynamicProperty.wrap"));
      wrapConfig = new BooleanConfigurer(m.isWrap());
      controls.add(wrapLabel, wrapConfig);





      controls.add("Editor.DynamicProperty.key_commands", keyCommandListConfig);

      numericConfig.addPropertyChangeListener(evt -> {
        final boolean isNumeric = numericConfig.booleanValue();
        minConfig.getControls().setVisible(isNumeric);
        minLabel.setVisible(isNumeric);
        maxConfig.getControls().setVisible(isNumeric);
        maxLabel.setVisible(isNumeric);
        wrapConfig.getControls().setVisible(isNumeric);
        wrapLabel.setVisible(isNumeric);
        keyCommandListConfig.repack();
      });

      numericConfig.fireUpdate();
    }

    @Override
    public Component getControls() {
      return controls;
    }

    protected String encodeConstraints() {
      return new SequenceEncoder(',')
        .append(numericConfig.getValueString())
        .append(minConfig.getValueString())
        .append(maxConfig.getValueString())
        .append(wrapConfig.getValueString())
        .getValue();
    }

    @Override
    public String getType() {
      final SequenceEncoder se = new SequenceEncoder(';');
      se.append(nameConfig.getValueString());
      se.append(encodeConstraints());
      se.append(keyCommandListConfig.getValueString());
      se.append(descConfig.getValueString());

      se.append(target.encode());
      se.append(propertiesFilter);
      se.append(restrictRange);
      se.append(range);
      se.append(fixedRange);
      se.append(rangeProperty);
      se.append(globalSetter.getSelectFromDeckExpression());

      return ID + se.getValue();
    }

    @Override
    public String getState() {
      return "";
    }
  }
}
