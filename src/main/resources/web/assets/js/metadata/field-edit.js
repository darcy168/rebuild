/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/
/* global FormulaDate, FormulaCalc */

const wpc = window.__PageConfig
const __gExtConfig = {}

const SHOW_REPEATABLE = ['TEXT', 'DATE', 'DATETIME', 'EMAIL', 'URL', 'PHONE', 'REFERENCE', 'CLASSIFICATION']
const SHOW_DEFAULTVALUE = ['TEXT', 'NTEXT', 'EMAIL', 'PHONE', 'URL', 'NUMBER', 'DECIMAL', 'DATE', 'DATETIME', 'BOOL', 'CLASSIFICATION', 'REFERENCE', 'N2NREFERENCE']

$(document).ready(function () {
  const dt = wpc.fieldType
  const extConfig = wpc.extConfig

  const $btn = $('.J_save').click(function () {
    if (!wpc.metaId) return
    let data = {
      fieldLabel: $val('#fieldLabel'),
      comments: $val('#comments'),
      nullable: $val('#fieldNullable'),
      creatable: $val('#fieldCreatable'),
      updatable: $val('#fieldUpdatable'),
      repeatable: $val('#fieldRepeatable'),
      queryable: $val('#fieldQueryable'),
    }
    if (data.fieldLabel === '') return RbHighbar.create($L('PlsInputSome,FieldName'))

    // 默认值
    let dv = $val('.J_defaultValue')
    if (dt === 'CLASSIFICATION' || dt === 'REFERENCE' || dt === 'N2NREFERENCE') {
      dv = $('.J_defaultValue').attr('data-value-id') || ''
      const odv = $('.J_defaultValue').attr('data-o') || ''
      if (dv === odv) dv = null
    }

    if (dv) {
      if (checkDefaultValue(dv, dt) === false) return
      else data.defaultValue = dv
    } else if (dv === '') {
      data.defaultValue = dv
    }

    const extConfigNew = { ...__gExtConfig }
    // 不同类型的配置
    $(`.J_for-${dt} .form-control, .J_for-${dt} .custom-control-input`).each(function () {
      const k = $(this).attr('id')
      extConfigNew[k] = $val(this)
    })
    // 单选型
    $(`.J_for-${dt} .custom-radio .custom-control-input:checked`).each(function () {
      const k = $(this).attr('name')
      extConfigNew[k] = $val(this)
    })

    if (!$same(extConfigNew, extConfig)) {
      data['extConfig'] = JSON.stringify(extConfigNew)
      if (Object.keys(extConfigNew).length === 0) data['extConfig'] = ''
    }

    data = $cleanMap(data)
    if (Object.keys(data).length === 0) {
      if (rb.env === 'dev') location.reload()
      else location.href = '../fields'
      return
    }

    const save = function () {
      data.metadata = {
        entity: 'MetaField',
        id: wpc.metaId,
      }

      $btn.button('loading')
      $.post('/admin/entity/field-update', JSON.stringify(data), function (res) {
        if (res.error_code === 0) {
          if (rb.env === 'dev') location.reload()
          else location.href = '../fields'
        } else {
          $btn.button('reset')
          RbHighbar.error(res.error_msg)
        }
      })
    }

    if (!$('#fieldNullable').prop('disabled') && !$('#fieldNullable').prop('checked') && !$('#fieldCreatable').prop('checked')) {
      RbAlert.create($L('SetNullAndCreateTips'), {
        confirm: function () {
          this.disabled(true)
          save()
        },
      })
    } else {
      save()
    }
  })

  $('#fieldNullable').attr('checked', $isTrue($('#fieldNullable').data('o')))
  $('#fieldCreatable').attr('checked', $isTrue($('#fieldCreatable').data('o')))
  $('#fieldUpdatable').attr('checked', $isTrue($('#fieldUpdatable').data('o')))
  $('#fieldRepeatable').attr('checked', $isTrue($('#fieldRepeatable').data('o')))
  $('#fieldQueryable').attr('checked', $isTrue($('#fieldQueryable').data('o')))

  // 设置扩展值
  for (let k in extConfig) {
    const $control = $(`#${k}`)
    if ($control.length === 1) {
      if ($control.attr('type') === 'checkbox') $control.attr('checked', $isTrue(extConfig[k]))
      else if ($control.prop('tagName') === 'DIV') $control.text(extConfig[k])
      else $control.val(extConfig[k])
    } else {
      $(`.custom-control-input[name="${k}"][value="${extConfig[k]}"]`).attr('checked', true)
    }
  }

  if (dt === 'PICKLIST' || dt === 'MULTISELECT') {
    _handlePicklist(dt)
  } else if (dt === 'SERIES') {
    _handleSeries()
  } else if (dt === 'DATE' || dt === 'DATETIME') {
    _handleDate(dt)
  } else if (dt === 'FILE' || dt === 'IMAGE') {
    _handleFile(extConfig.uploadNumber)
  } else if (dt === 'CLASSIFICATION') {
    _handleClassification(extConfig.classification)
  } else if (dt === 'REFERENCE') {
    _handleReference()
  } else if (dt === 'N2NREFERENCE') {
    _handleReference(true)
  } else if (dt === 'BOOL') {
    const $dv = $('.J_defaultValue')
    if ($dv.data('o')) $dv.val($dv.data('o'))
  } else if (dt === 'BARCODE') {
    $('.J_fieldAttrs input').attr('disabled', true)
  } else if (dt === 'NUMBER' || dt === 'DECIMAL') {
    _handleNumber(extConfig.calcFormula)
  }

  // 显示重复值选项
  if (SHOW_REPEATABLE.includes(dt) && wpc.fieldName !== 'approvalId') {
    $('#fieldRepeatable').parents('.custom-control').removeClass('hide')
  }
  // 默认值
  if (!SHOW_DEFAULTVALUE.includes(dt)) {
    $('#defaultValue').remove()
  }

  // 内建字段
  if (wpc.fieldBuildin) {
    $('.J_fieldAttrs, .J_for-STATE, .J_for-REFERENCE-filter').remove()
  }

  // 只读属性
  delete extConfig['classification']
  delete extConfig['stateClass']

  $('.J_del').click(function () {
    if (!wpc.isSuperAdmin) {
      RbHighbar.error($L('OnlyAdminCanSome,DeleteField'))
      return
    }

    RbAlert.create($L('DeleteFieldConfirm'), $L('DeleteField'), {
      type: 'danger',
      confirmText: $L('Delete'),
      confirm: function () {
        this.disabled(true)
        $.post(`/admin/entity/field-drop?id=${wpc.metaId}`, (res) => {
          if (res.error_code === 0) {
            this.hide()
            RbHighbar.success($L('SomeDeleted,Field'))
            setTimeout(function () {
              location.replace('../fields')
            }, 1500)
          } else RbHighbar.error(res.error_msg)
        })
      },
      call: function () {
        $countdownButton($(this._dlg).find('.btn-danger'))
      },
    })
  })
})

// Render item to PickList box
const picklistItemRender = function (data) {
  const $item = $(`<li class="dd-item" data-key="${data.id}"><div class="dd-handle">${data.text}</div></li>`).appendTo('#picklist-items')
  if ($isTrue(data['default'])) $item.addClass('default')
}

// Check incorrect?
// Also see RbFormElement#checkHasError in rb-forms.js
const checkDefaultValue = function (v, t) {
  let valid = true
  if (t === 'NUMBER' || t === 'DECIMAL') {
    valid = !isNaN(v)
  } else if (t === 'URL') {
    valid = $regex.isUrl(v)
  } else if (t === 'EMAIL') {
    valid = $regex.isMail(v)
  } else if (t === 'PHONE') {
    valid = $regex.isTel(v)
  }
  if (valid === false) RbHighbar.create($L('SomeInvalid,DefaultValue'))
  return valid
}

const _handlePicklist = function (dt) {
  $.get(`/admin/field/picklist-gets?entity=${wpc.entityName}&field=${wpc.fieldName}&isAll=false`, function (res) {
    if (res.data.length === 0) {
      $('#picklist-items li').text($L('PlsAddOption'))
      return
    }
    $('#picklist-items').empty()
    $(res.data).each(function () {
      picklistItemRender(this)
    })
    if (res.data.length > 5) $('#picklist-items').parent().removeClass('autoh')
  })
  $('.J_picklist-edit').click(() => RbModal.create(`/p/admin/metadata/picklist-editor?entity=${wpc.entityName}&field=${wpc.fieldName}&multi=${dt === 'MULTISELECT'}`, $L('SetOptionList')))
}

const _handleSeries = function () {
  $('.J_fieldAttrs input').attr('disabled', true)
  $('.J_series-reindex').click(() => {
    RbAlert.create($L('AppendSeriesConfirm'), {
      confirm: function () {
        this.disabled(true)
        $.post(`/admin/field/series-reindex?entity=${wpc.entityName}&field=${wpc.fieldName}`, () => {
          this.hide()
          RbHighbar.success($L('SomeSuccess,AppendSeries'))
        })
      },
    })
  })
}

const _handleDate = function (dt) {
  $('.J_defaultValue')
    .datetimepicker({
      format: dt === 'DATE' ? 'yyyy-mm-dd' : 'yyyy-mm-dd hh:ii:ss',
      minView: dt === 'DATE' ? 2 : 0,
      clearBtn: true,
    })
    .attr('readonly', true)

  $(`<button class="btn btn-secondary mw-auto" type="button" title="${$L('DateFormula')}"><i class="icon zmdi zmdi-settings-square"></i></button>`)
    .appendTo('.J_defaultValue-append')
    .click(() => renderRbcomp(<FormulaDate type={dt} onConfirm={(expr) => $('.J_defaultValue').val(expr)} />))
}

const _handleFile = function (uploadNumber) {
  if (uploadNumber) {
    uploadNumber = uploadNumber.split(',')
    uploadNumber[0] = ~~uploadNumber[0]
    uploadNumber[1] = ~~uploadNumber[1]
    $('.J_minmax b').eq(0).text(uploadNumber[0])
    $('.J_minmax b').eq(1).text(uploadNumber[1])
  } else {
    uploadNumber = [0, 9]
  }

  $('input.bslider')
    .slider({ value: uploadNumber })
    .on('change', function (e) {
      const v = e.value.newValue
      $setTimeout(
        () => {
          $('.J_minmax b').eq(0).text(v[0])
          $('.J_minmax b').eq(1).text(v[1])
          $('#fieldNullable').attr('checked', v[0] <= 0)
        },
        200,
        'bslider-change'
      )
    })
  $('#fieldNullable').attr('disabled', true)
}

const _handleClassification = function (useClassification) {
  const $dv = $('.J_defaultValue')
  const $dvClear = $('.J_defaultValue-clear').click(() => {
    $dv.attr('data-value-id', '').val('')
    $dvClear.addClass('hide')
  })

  let _ClassificationSelector
  function _showSelector(data) {
    if (_ClassificationSelector) {
      _ClassificationSelector.show()
    } else {
      renderRbcomp(
        // eslint-disable-next-line react/jsx-no-undef
        <ClassificationSelector
          entity={wpc.entityName}
          field={wpc.fieldName}
          label={$L('DefaultValue')}
          openLevel={data.openLevel}
          onSelect={(s) => {
            $dv.attr('data-value-id', s.id).val(s.text)
            $dvClear.removeClass('hide')
          }}
        />,
        null,
        function () {
          _ClassificationSelector = this
        }
      )
    }
  }

  const $append = $(`<button class="btn btn-secondary mw-auto" type="button" title="${$L('SelectSome,DefaultValue')}"><i class="icon zmdi zmdi-search"></i></button>`).appendTo(
    '.J_defaultValue-append'
  )

  $.get(`/admin/metadata/classification/info?id=${useClassification}`, (res) => {
    $('#useClassification a')
      .attr({ href: `${rb.baseUrl}/admin/metadata/classification/${useClassification}` })
      .text(res.data.name)

    $dv.attr('readonly', true)
    $append.click(() => _showSelector(res.data))
  })

  _loadRefsLabel($dv, $dvClear)
}

const _handleReference = function (isN2N) {
  const referenceEntity = $('.J_referenceEntity').data('refentity')

  // 数据过滤
  let dataFilter = (wpc.extConfig || {}).referenceDataFilter
  const saveFilter = function (res) {
    if (res && res.items && res.items.length > 0) {
      $('#referenceDataFilter').text(`${$L('AdvFiletrSeted')} (${res.items.length})`)
      dataFilter = res
    } else {
      $('#referenceDataFilter').text($L('ClickSet'))
      dataFilter = null
    }
    __gExtConfig.referenceDataFilter = dataFilter
  }
  dataFilter && saveFilter(dataFilter)

  let advFilter
  $('#referenceDataFilter').click(() => {
    if (advFilter) {
      advFilter.show()
    } else {
      renderRbcomp(<AdvFilter title={$L('SetAdvFiletr')} inModal={true} canNoFilters={true} entity={referenceEntity} filter={dataFilter} confirm={saveFilter} />, null, function () {
        advFilter = this
      })
    }
  })

  // 默认值
  const $dv = $('.J_defaultValue')
  const $dvClear = $('.J_defaultValue-clear').click(() => {
    $dv.attr('data-value-id', '').val('')
    $dvClear.addClass('hide')
  })

  let _ReferenceSearcher
  function _showSearcher() {
    if (_ReferenceSearcher) {
      _ReferenceSearcher.show()
    } else {
      const searchUrl = `${rb.baseUrl}/commons/search/reference-search?field=${wpc.fieldName}.${wpc.entityName}`
      // eslint-disable-next-line react/jsx-no-undef
      renderRbcomp(<ReferenceSearcher url={searchUrl} title={$L('SelectSome,DefaultValue')} />, function () {
        _ReferenceSearcher = this
      })
    }
  }

  const $append = $(`<button class="btn btn-secondary mw-auto" type="button" title="${$L('SelectSome,DefaultValue')}"><i class="icon zmdi zmdi-search"></i></button>`).appendTo(
    '.J_defaultValue-append'
  )
  $dv.attr('readonly', true)
  $append.click(() => _showSearcher())

  window.referenceSearch__call = function (selected) {
    let val
    if (isN2N) {
      let keepVal = $dv.attr('data-value-id')
      if (keepVal) keepVal = keepVal.split(',')
      else keepVal = []

      selected.forEach((s) => {
        if (!keepVal.contains(s)) keepVal.push(s)
      })
      val = keepVal.slice(0, 20).join(',')
    } else {
      val = selected[0]
    }

    $dv.attr('data-value-id', val).val(val)
    _loadRefsLabel($dv, $dvClear)
    _ReferenceSearcher.hide()
  }

  _loadRefsLabel($dv, $dvClear)
}

const _loadRefsLabel = function ($dv, $dvClear) {
  const dvid = $dv.val()
  if (dvid) {
    $.get(`/commons/search/read-labels?ids=${dvid}&ignoreMiss=true`, (res) => {
      if (res.data) {
        const ids = []
        const labels = []
        for (let k in res.data) {
          ids.push(k)
          labels.push(res.data[k])
        }

        $dv.attr('data-value-id', ids.join(','))
        $dv.val(labels.join(', '))
      }
    })

    $dvClear && $dvClear.removeClass('hide')
  }
}

let FIELDS_CACHE
const _handleNumber = function (calcFormula) {
  const $el = $('#calcFormula2')
  function _call(s) {
    $('#calcFormula').val(s || '')
    $el.text(FormulaCalc.textFormula(s, FIELDS_CACHE))
  }

  if (!FIELDS_CACHE) {
    $.get(`/commons/metadata/fields?entity=${wpc.entityName}`, (res) => {
      const fs = []
      res.data.forEach((item) => {
        if ((item.type === 'NUMBER' || item.type === 'DECIMAL') && item.name !== wpc.fieldName) {
          fs.push([item.name, item.label])
        }
      })

      FIELDS_CACHE = fs
      if (calcFormula) _call(calcFormula)
    })
  }

  $el.click(() => renderRbcomp(<FormulaCalc onConfirm={_call} fields={FIELDS_CACHE} />))
}
